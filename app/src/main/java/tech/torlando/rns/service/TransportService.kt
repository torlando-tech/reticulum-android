package tech.torlando.rns.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import tech.torlando.rns.MainActivity
import tech.torlando.rns.R
import tech.torlando.rns.binding.ReticulumBinding
import tech.torlando.rns.data.AnnounceEntry
import tech.torlando.rns.data.DiscoveredInterface
import tech.torlando.rns.data.InterfaceConfig
import tech.torlando.rns.data.InterfaceStats
import tech.torlando.rns.data.PathEntry
import tech.torlando.rns.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TransportService : Service() {

    companion object {
        private const val TAG = "TransportService"
        private const val CHANNEL_ID = "reticulum_transport"
        private const val NOTIFICATION_ID = 1
        private const val STATS_POLL_INTERVAL_MS = 2000L

        const val ACTION_START = "tech.torlando.rns.START"
        const val ACTION_STOP = "tech.torlando.rns.STOP"
    }

    inner class LocalBinder : Binder() {
        val service: TransportService get() = this@TransportService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var binding: ReticulumBinding? = null
    private var statsJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var prefs: PreferencesManager

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

    private val _discoveredInterfaces = MutableStateFlow<List<DiscoveredInterface>>(emptyList())
    val discoveredInterfaces: StateFlow<List<DiscoveredInterface>> = _discoveredInterfaces.asStateFlow()

    private val _discoveryEnabled = MutableStateFlow(false)
    val discoveryEnabled: StateFlow<Boolean> = _discoveryEnabled.asStateFlow()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTransport()
                stopSelf()
            }
            ACTION_START -> {
                // Explicit start from UI — safe to start foreground
                startForeground(
                    NOTIFICATION_ID,
                    createNotification("Starting..."),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
                if (_serviceState.value !is ServiceState.Running &&
                    _serviceState.value !is ServiceState.Starting
                ) {
                    startTransportFromPrefs()
                }
            }
            else -> {
                // Null intent = system restart via START_STICKY
                try {
                    startForeground(
                        NOTIFICATION_ID,
                        createNotification("Starting..."),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                    )
                    if (_serviceState.value !is ServiceState.Running &&
                        _serviceState.value !is ServiceState.Starting
                    ) {
                        startTransportFromPrefs()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Cannot restart foreground service from background, stopping", e)
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopTransport()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startTransportFromPrefs() {
        serviceScope.launch {
            val transportEnabled = prefs.transportEnabled.first()
            val shareInstance = prefs.shareInstance.first()
            val sharedInstancePort = prefs.sharedInstancePort.first()
            val instanceControlPort = prefs.instanceControlPort.first()
            val interfacesJson = prefs.interfacesJson.first()
            val interfaces = InterfaceConfig.fromJson(interfacesJson)
            val publishBlackhole = prefs.publishBlackhole.first()
            val blackholeSources = prefs.blackholeSources.first()
            startTransport(
                transportEnabled = transportEnabled,
                shareInstance = shareInstance,
                sharedInstancePort = sharedInstancePort,
                instanceControlPort = instanceControlPort,
                interfaces = interfaces,
                publishBlackhole = publishBlackhole,
                blackholeSources = blackholeSources,
            )
        }
    }

    fun startTransport(
        transportEnabled: Boolean,
        shareInstance: Boolean = true,
        sharedInstancePort: Int = 0,
        instanceControlPort: Int = 0,
        interfaces: List<InterfaceConfig>,
        publishBlackhole: Boolean = false,
        blackholeSources: String = "",
    ) {
        if (_serviceState.value is ServiceState.Running || _serviceState.value is ServiceState.Starting ||
            _serviceState.value is ServiceState.Stopping
        ) return

        _serviceState.value = ServiceState.Starting
        acquireWakeLock()

        serviceScope.launch(Dispatchers.IO) {
            try {
                val storagePath = filesDir.absolutePath
                val b = ReticulumBinding(storagePath, this@TransportService)
                b.initialize(
                    transportEnabled = transportEnabled,
                    shareInstance = shareInstance,
                    sharedInstancePort = sharedInstancePort,
                    instanceControlPort = instanceControlPort,
                    interfaces = interfaces,
                    publishBlackhole = publishBlackhole,
                    blackholeSources = blackholeSources,
                )
                binding = b

                _transportIdentity.value = b.getTransportIdentityHash()
                _serviceState.value = ServiceState.Running

                updateNotification("Running")
                startStatsPolling()

                Log.i(TAG, "Transport started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start transport", e)
                val message = friendlyErrorMessage(e)
                _serviceState.value = ServiceState.Error(message)
                updateNotification("Error: $message")
                releaseWakeLock()
            }
        }
    }

    fun stopTransport() {
        if (_serviceState.value is ServiceState.Stopping || _serviceState.value is ServiceState.Stopped) return

        statsJob?.cancel()
        statsJob = null
        _serviceState.value = ServiceState.Stopping

        serviceScope.launch(Dispatchers.IO) {
            try {
                binding?.shutdown()
            } catch (e: Exception) {
                Log.e(TAG, "Error during shutdown", e)
            }
            binding = null
            _interfaceStats.value = emptyList()
            _pathTable.value = emptyList()
            _announceTable.value = emptyList()
            _discoveredInterfaces.value = emptyList()
            _discoveryEnabled.value = false
            _transportIdentity.value = null
            _serviceState.value = ServiceState.Stopped
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    fun enableDiscovery() {
        val b = binding ?: return
        serviceScope.launch(Dispatchers.IO) {
            val success = b.enableDiscovery()
            _discoveryEnabled.value = success
            if (success) {
                Log.i(TAG, "Interface discovery enabled")
            } else {
                Log.w(TAG, "Failed to enable interface discovery")
            }
        }
    }

    fun refreshDiscoveredInterfaces() {
        val b = binding ?: return
        serviceScope.launch(Dispatchers.IO) {
            _discoveredInterfaces.value = b.getDiscoveredInterfaces()
        }
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = serviceScope.launch(Dispatchers.IO) {
            while (true) {
                delay(STATS_POLL_INTERVAL_MS)
                val b = binding ?: break
                try {
                    _interfaceStats.value = b.getInterfaceStats()
                    _pathTable.value = b.getPathTable()
                    _announceTable.value = b.getAnnounceTable()
                    if (_discoveryEnabled.value) {
                        _discoveredInterfaces.value = b.getDiscoveredInterfaces()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error polling stats", e)
                }
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ReticulumTransport::TransportWakeLock",
            )
        }
        wakeLock?.acquire()
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reticulum Transport",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Reticulum transport node service"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun createNotification(status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TransportService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Reticulum Transport")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_notification, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun friendlyErrorMessage(e: Exception): String {
        val msg = e.message ?: e.cause?.message ?: ""
        return when {
            msg.contains("EADDRINUSE", ignoreCase = true) ||
                msg.contains("Address already in use", ignoreCase = true) ||
                msg.contains("errno 98", ignoreCase = true) ->
                "Port already in use. Another Reticulum instance or app may be using the same AutoInterface port. " +
                    "Try stopping other Reticulum apps first."
            msg.contains("Permission denied", ignoreCase = true) ||
                msg.contains("EACCES", ignoreCase = true) ->
                "Permission denied while setting up network interfaces."
            msg.contains("Network is unreachable", ignoreCase = true) ->
                "Network is unreachable. Check your connection and try again."
            else -> msg.ifBlank { "Unknown error" }
        }
    }

    private fun updateNotification(status: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, createNotification(status))
    }
}

sealed class ServiceState {
    data object Stopped : ServiceState()
    data object Starting : ServiceState()
    data object Running : ServiceState()
    data object Stopping : ServiceState()
    data class Error(val message: String) : ServiceState()
}
