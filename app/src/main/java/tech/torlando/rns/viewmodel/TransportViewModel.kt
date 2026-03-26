package tech.torlando.rns.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.json.JSONArray
import org.json.JSONObject
import tech.torlando.rns.binding.ConfigGenerator
import tech.torlando.rns.data.AnnounceEntry
import tech.torlando.rns.data.DiscoveredInterface
import tech.torlando.rns.data.InterfaceConfig
import tech.torlando.rns.data.InterfaceStats
import tech.torlando.rns.data.PathEntry
import tech.torlando.rns.data.PreferencesManager
import tech.torlando.rns.service.IRnsCallback
import tech.torlando.rns.service.IRnsService
import tech.torlando.rns.service.ServiceSnapshot
import tech.torlando.rns.service.ServiceState
import tech.torlando.rns.service.TransportService
import tech.torlando.rns.stats.data.HistoryBuffer
import tech.torlando.rns.stats.data.InterfaceHistoryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransportViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "TransportViewModel"
    }

    private val prefs = PreferencesManager(application)
    private var rnsService: IRnsService? = null
    private var pendingConfigJson: String? = null
    private var pendingRestartAfterDisconnect = false

    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Stopped)
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    private val _interfaceStats = MutableStateFlow<List<InterfaceStats>>(emptyList())
    val interfaceStats: StateFlow<List<InterfaceStats>> = _interfaceStats.asStateFlow()

    private val _pathTable = MutableStateFlow<List<PathEntry>>(emptyList())
    val pathTable: StateFlow<List<PathEntry>> = _pathTable.asStateFlow()

    private val _announceTable = MutableStateFlow<List<AnnounceEntry>>(emptyList())
    val announceTable: StateFlow<List<AnnounceEntry>> = _announceTable.asStateFlow()

    private val _transportIdentity = MutableStateFlow<String?>(null)
    val transportIdentity: StateFlow<String?> = _transportIdentity.asStateFlow()

    private val _interfaces = MutableStateFlow<List<InterfaceConfig>>(emptyList())
    val interfaces: StateFlow<List<InterfaceConfig>> = _interfaces.asStateFlow()

    private val _discoveredInterfaces = MutableStateFlow<List<DiscoveredInterface>>(emptyList())
    val discoveredInterfaces: StateFlow<List<DiscoveredInterface>> = _discoveredInterfaces.asStateFlow()

    private val _discoveryEnabled = MutableStateFlow(false)
    val discoveryEnabled: StateFlow<Boolean> = _discoveryEnabled.asStateFlow()

    private val _isConnectedToSharedInstance = MutableStateFlow(false)
    val isConnectedToSharedInstance: StateFlow<Boolean> = _isConnectedToSharedInstance.asStateFlow()

    private val _pendingRestart = MutableStateFlow(false)
    val pendingRestart: StateFlow<Boolean> = _pendingRestart.asStateFlow()

    // Per-interface history buffers for traffic speed charts
    private val historyBuffers = mutableMapOf<String, HistoryBuffer>()
    private val _interfaceHistory = MutableStateFlow<Map<String, List<InterfaceHistoryPoint>>>(emptyMap())
    val interfaceHistory: StateFlow<Map<String, List<InterfaceHistoryPoint>>> = _interfaceHistory.asStateFlow()

    val transportEnabled = prefs.transportEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, true,
    )

    val shareInstance = prefs.shareInstance.stateIn(
        viewModelScope, SharingStarted.Eagerly, true,
    )

    val sharedInstancePort = prefs.sharedInstancePort.stateIn(
        viewModelScope, SharingStarted.Eagerly, 0,
    )

    val instanceControlPort = prefs.instanceControlPort.stateIn(
        viewModelScope, SharingStarted.Eagerly, 0,
    )

    val publishBlackhole = prefs.publishBlackhole.stateIn(
        viewModelScope, SharingStarted.Eagerly, false,
    )

    val blackholeSources = prefs.blackholeSources.stateIn(
        viewModelScope, SharingStarted.Eagerly, "",
    )

    private val rnsCallback = object : IRnsCallback.Stub() {
        override fun onUpdate(json: String?) {
            if (json == null) return
            try {
                val snapshot = ServiceSnapshot.fromJson(json)
                applySnapshot(snapshot)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse snapshot", e)
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = IRnsService.Stub.asInterface(binder)
            rnsService = svc

            try {
                svc.registerCallback(rnsCallback)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register callback", e)
            }

            // If there's a pending start, send it now
            pendingConfigJson?.let { config ->
                pendingConfigJson = null
                try {
                    svc.start(config)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to call start on service", e)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            rnsService = null
            clearState()

            if (pendingRestartAfterDisconnect) {
                pendingRestartAfterDisconnect = false
                startService()
            } else {
                // Auto-rebind to catch system-restarted service
                rebind()
            }
        }
    }

    init {
        rebind()

        viewModelScope.launch {
            prefs.interfacesJson.collect { json ->
                _interfaces.value = InterfaceConfig.fromJson(json)
            }
        }
    }

    override fun onCleared() {
        try {
            rnsService?.unregisterCallback(rnsCallback)
        } catch (_: Exception) {}
        try {
            getApplication<Application>().unbindService(connection)
        } catch (_: Exception) {}
        super.onCleared()
    }

    fun startService() {
        _pendingRestart.value = false

        viewModelScope.launch {
            val configJson = buildConfigJson()

            val ctx = getApplication<Application>()
            val intent = Intent(ctx, TransportService::class.java).apply {
                action = TransportService.ACTION_START
            }
            ctx.startForegroundService(intent)

            val svc = rnsService
            if (svc != null) {
                try {
                    svc.start(configJson)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to call start", e)
                }
            } else {
                // Not yet bound — save config for onServiceConnected
                pendingConfigJson = configJson
                rebind()
            }
        }
    }

    fun stopService() {
        try {
            rnsService?.stop()
        } catch (_: Exception) {}
        // Process dies → onServiceDisconnected fires → state clears
    }

    fun restartService() {
        _pendingRestart.value = false
        pendingRestartAfterDisconnect = true
        try {
            rnsService?.stop()
        } catch (_: Exception) {}
        // Process dies → onServiceDisconnected → startService()
    }

    fun enableDiscovery() {
        try {
            rnsService?.enableDiscovery()
        } catch (_: Exception) {}
    }

    fun refreshDiscoveredInterfaces() {
        // Discovered interfaces are refreshed automatically during polling.
        // This is a no-op now but kept for API compatibility.
    }

    // --- Interface config management (local prefs, doesn't cross AIDL) ---

    fun addInterface(config: InterfaceConfig) {
        viewModelScope.launch {
            val current = _interfaces.value.toMutableList()
            current.add(config)
            _interfaces.value = current
            prefs.setInterfacesJson(serializeInterfaces(current))
            if (_serviceState.value is ServiceState.Running) {
                _pendingRestart.value = true
            }
        }
    }

    fun removeInterface(index: Int) {
        viewModelScope.launch {
            val current = _interfaces.value.toMutableList()
            if (index in current.indices) {
                current.removeAt(index)
                _interfaces.value = current
                prefs.setInterfacesJson(serializeInterfaces(current))
                if (_serviceState.value is ServiceState.Running) {
                    _pendingRestart.value = true
                }
            }
        }
    }

    fun updateInterface(index: Int, config: InterfaceConfig) {
        viewModelScope.launch {
            val current = _interfaces.value.toMutableList()
            if (index in current.indices) {
                current[index] = config
                _interfaces.value = current
                prefs.setInterfacesJson(serializeInterfaces(current))
                if (_serviceState.value is ServiceState.Running) {
                    _pendingRestart.value = true
                }
            }
        }
    }

    fun toggleInterfaceEnabled(index: Int) {
        val config = _interfaces.value.getOrNull(index) ?: return
        updateInterface(index, config.withEnabled(!config.enabled))
    }

    // --- Settings ---

    fun setTransportEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setTransportEnabled(enabled) }
    }

    fun setShareInstance(enabled: Boolean) {
        viewModelScope.launch { prefs.setShareInstance(enabled) }
    }

    fun setSharedInstancePort(port: Int) {
        viewModelScope.launch { prefs.setSharedInstancePort(port) }
    }

    fun setInstanceControlPort(port: Int) {
        viewModelScope.launch { prefs.setInstanceControlPort(port) }
    }

    fun setPublishBlackhole(enabled: Boolean) {
        viewModelScope.launch { prefs.setPublishBlackhole(enabled) }
    }

    fun setBlackholeSources(sources: String) {
        viewModelScope.launch { prefs.setBlackholeSources(sources) }
    }

    // --- Private helpers ---

    private fun applySnapshot(snapshot: ServiceSnapshot) {
        _serviceState.value = when (snapshot.state) {
            "running" -> ServiceState.Running
            "starting" -> ServiceState.Starting
            "stopping" -> ServiceState.Stopping
            "error" -> ServiceState.Error(snapshot.error ?: "Unknown error")
            else -> ServiceState.Stopped
        }
        _transportIdentity.value = snapshot.identity
        _isConnectedToSharedInstance.value = snapshot.connectedToSharedInstance
        _interfaceStats.value = snapshot.interfaces
        _pathTable.value = snapshot.pathTable
        _announceTable.value = snapshot.announceQueue
        _discoveredInterfaces.value = snapshot.discoveredInterfaces
        _discoveryEnabled.value = snapshot.discoveryEnabled

        if (snapshot.interfaces.isNotEmpty()) {
            recordHistory(snapshot.interfaces)
        }
    }

    private fun clearState() {
        _serviceState.value = ServiceState.Stopped
        _interfaceStats.value = emptyList()
        _pathTable.value = emptyList()
        _announceTable.value = emptyList()
        _discoveredInterfaces.value = emptyList()
        _discoveryEnabled.value = false
        _isConnectedToSharedInstance.value = false
        _transportIdentity.value = null
    }

    private fun rebind() {
        val ctx = getApplication<Application>()
        try {
            ctx.bindService(
                Intent(ctx, TransportService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to bind to service", e)
        }
    }

    private suspend fun buildConfigJson(): String {
        val transportEnabled = prefs.transportEnabled.first()
        val shareInstance = prefs.shareInstance.first()
        val sharedInstancePort = prefs.sharedInstancePort.first()
        val instanceControlPort = prefs.instanceControlPort.first()
        val interfacesJson = prefs.interfacesJson.first()
        val allInterfaces = InterfaceConfig.fromJson(interfacesJson)
        val publishBlackhole = prefs.publishBlackhole.first()
        val blackholeSources = prefs.blackholeSources.first()

        val configIni = ConfigGenerator.generate(
            interfaces = allInterfaces,
            transportEnabled = transportEnabled,
            shareInstance = shareInstance,
            sharedInstancePort = sharedInstancePort,
            instanceControlPort = instanceControlPort,
            publishBlackhole = publishBlackhole,
            blackholeSources = blackholeSources,
        )

        val rnodeArr = JSONArray()
        for (rc in allInterfaces.filterIsInstance<InterfaceConfig.RNodeInterface>().filter { it.enabled }) {
            rnodeArr.put(JSONObject().apply {
                put("name", rc.name)
                put("connectionMode", rc.connectionMode)
                put("targetDevice", rc.targetDevice)
                put("frequency", rc.frequency)
                put("bandwidth", rc.bandwidth)
                put("spreadingFactor", rc.spreadingFactor)
                put("codingRate", rc.codingRate)
                put("txPower", rc.txPower)
            })
        }

        return JSONObject().apply {
            put("configIni", configIni)
            put("rnodeInterfaces", rnodeArr)
        }.toString()
    }

    private fun recordHistory(stats: List<InterfaceStats>) {
        val now = System.currentTimeMillis()
        val activeNames = mutableSetOf<String>()
        for (stat in stats) {
            activeNames.add(stat.name)
            val buffer = historyBuffers.getOrPut(stat.name) { HistoryBuffer() }
            buffer.add(InterfaceHistoryPoint(timestamp = now, rxBytes = stat.rxb, txBytes = stat.txb))
        }
        historyBuffers.keys.removeAll { it !in activeNames }
        _interfaceHistory.value = historyBuffers.mapValues { it.value.toList() }
    }

    private fun serializeInterfaces(interfaces: List<InterfaceConfig>): String {
        val arr = JSONArray()
        for (iface in interfaces) {
            val obj = JSONObject()
            obj.put("name", iface.name)
            obj.put("enabled", iface.enabled)
            obj.put("network_name", iface.networkName)
            obj.put("passphrase", iface.passphrase)
            obj.put("ifac_size", iface.ifacSize)
            obj.put("interface_mode", iface.interfaceMode)
            when (iface) {
                is InterfaceConfig.TcpClient -> {
                    obj.put("type", "tcp_client")
                    obj.put("target_host", iface.targetHost)
                    obj.put("target_port", iface.targetPort)
                    obj.put("bootstrap_only", iface.bootstrapOnly)
                    obj.put("socks_proxy_enabled", iface.socksProxyEnabled)
                    obj.put("socks_proxy_host", iface.socksProxyHost)
                    obj.put("socks_proxy_port", iface.socksProxyPort)
                }
                is InterfaceConfig.TcpServer -> {
                    obj.put("type", "tcp_server")
                    obj.put("listen_ip", iface.listenIp)
                    obj.put("listen_port", iface.listenPort)
                }
                is InterfaceConfig.AutoInterface -> {
                    obj.put("type", "auto")
                    obj.put("group_id", iface.groupId)
                    obj.put("discovery_scope", iface.discoveryScope)
                }
                is InterfaceConfig.UdpInterface -> {
                    obj.put("type", "udp")
                    obj.put("listen_ip", iface.listenIp)
                    obj.put("listen_port", iface.listenPort)
                    obj.put("forward_ip", iface.forwardIp)
                    obj.put("forward_port", iface.forwardPort)
                }
                is InterfaceConfig.I2PInterface -> {
                    obj.put("type", "i2p")
                    obj.put("peers", iface.peers)
                    obj.put("connectable", iface.connectable)
                }
                is InterfaceConfig.RNodeInterface -> {
                    obj.put("type", "rnode")
                    obj.put("connection_mode", iface.connectionMode)
                    obj.put("target_device", iface.targetDevice)
                    obj.put("frequency", iface.frequency)
                    obj.put("bandwidth", iface.bandwidth)
                    obj.put("spreading_factor", iface.spreadingFactor)
                    obj.put("coding_rate", iface.codingRate)
                    obj.put("tx_power", iface.txPower)
                }
            }
            arr.put(obj)
        }
        return arr.toString()
    }
}
