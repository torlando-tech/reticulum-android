package io.reticulum.transport.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.reticulum.transport.data.AnnounceEntry
import io.reticulum.transport.data.DiscoveredInterface
import io.reticulum.transport.data.InterfaceConfig
import io.reticulum.transport.data.InterfaceStats
import io.reticulum.transport.data.PathEntry
import io.reticulum.transport.data.PreferencesManager
import io.reticulum.transport.service.ServiceState
import io.reticulum.transport.service.TransportService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class TransportViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private var service: TransportService? = null

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

    val transportEnabled = prefs.transportEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, true,
    )

    val shareInstance = prefs.shareInstance.stateIn(
        viewModelScope, SharingStarted.Eagerly, true,
    )

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as TransportService.LocalBinder).service
            service = svc

            viewModelScope.launch {
                svc.serviceState.collect { _serviceState.value = it }
            }
            viewModelScope.launch {
                svc.interfaceStats.collect { _interfaceStats.value = it }
            }
            viewModelScope.launch {
                svc.pathTable.collect { _pathTable.value = it }
            }
            viewModelScope.launch {
                svc.announceTable.collect { _announceTable.value = it }
            }
            viewModelScope.launch {
                svc.transportIdentity.collect { _transportIdentity.value = it }
            }
            viewModelScope.launch {
                svc.discoveredInterfaces.collect { _discoveredInterfaces.value = it }
            }
            viewModelScope.launch {
                svc.discoveryEnabled.collect { _discoveryEnabled.value = it }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            _serviceState.value = ServiceState.Stopped
        }
    }

    init {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, TransportService::class.java)
        ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE)

        viewModelScope.launch {
            prefs.interfacesJson.collect { json ->
                _interfaces.value = InterfaceConfig.fromJson(json)
            }
        }
    }

    override fun onCleared() {
        try {
            getApplication<Application>().unbindService(connection)
        } catch (_: Exception) {}
        super.onCleared()
    }

    fun startService() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, TransportService::class.java).apply {
            action = TransportService.ACTION_START
        }
        ctx.startForegroundService(intent)
        ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun stopService() {
        val svc = service ?: return
        svc.stopTransport()
        // Stop the Android service once shutdown completes (state becomes Stopped)
        viewModelScope.launch {
            svc.serviceState.collect { state ->
                if (state is ServiceState.Stopped) {
                    val ctx = getApplication<Application>()
                    ctx.stopService(Intent(ctx, TransportService::class.java))
                    return@collect
                }
            }
        }
    }

    fun addInterface(config: InterfaceConfig) {
        viewModelScope.launch {
            val current = _interfaces.value.toMutableList()
            current.add(config)
            _interfaces.value = current
            prefs.setInterfacesJson(serializeInterfaces(current))
        }
    }

    fun removeInterface(index: Int) {
        viewModelScope.launch {
            val current = _interfaces.value.toMutableList()
            if (index in current.indices) {
                current.removeAt(index)
                _interfaces.value = current
                prefs.setInterfacesJson(serializeInterfaces(current))
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
            }
        }
    }

    fun setTransportEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setTransportEnabled(enabled) }
    }

    fun setShareInstance(enabled: Boolean) {
        viewModelScope.launch { prefs.setShareInstance(enabled) }
    }

    fun enableDiscovery() {
        service?.enableDiscovery()
    }

    fun refreshDiscoveredInterfaces() {
        service?.refreshDiscoveredInterfaces()
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
            }
            arr.put(obj)
        }
        return arr.toString()
    }

}
