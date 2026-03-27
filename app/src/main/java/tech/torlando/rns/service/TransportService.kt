package tech.torlando.rns.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.app.ForegroundServiceStartNotAllowedException
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import tech.torlando.rns.MainActivity
import tech.torlando.rns.R
import tech.torlando.rns.binding.ReticulumBinding
import tech.torlando.rns.data.InterfaceConfig
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class TransportService : Service() {

    companion object {
        private const val TAG = "TransportService"
        private const val CHANNEL_ID = "reticulum_transport"
        private const val NOTIFICATION_ID = 1
        private const val STATS_POLL_INTERVAL_MS = 2000L
        private const val SAVED_CONFIG_FILE = "saved_config.json"

        const val ACTION_START = "tech.torlando.rns.START"
        const val ACTION_STOP = "tech.torlando.rns.STOP"
    }

    private var binding: ReticulumBinding? = null
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var currentState = "stopped"
    @Volatile private var currentError: String? = null
    @Volatile private var discoveryEnabled = false
    @Volatile private var cachedSnapshot = ServiceSnapshot(state = "stopped").toJson()

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var pollingFuture: ScheduledFuture<*>? = null

    // Simple single-callback — only one ViewModel connects
    @Volatile private var callback: IRnsCallback? = null

    private val binder = object : IRnsService.Stub() {
        override fun start(configJson: String?) {
            if (configJson == null) return
            executor.execute { doStart(configJson) }
        }

        override fun stop() {
            executor.execute { doStop() }
        }

        override fun getSnapshot(): String = cachedSnapshot

        override fun registerCallback(cb: IRnsCallback?) {
            callback = cb
            if (cb != null) {
                try {
                    cb.onUpdate(cachedSnapshot)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to push initial snapshot", e)
                    callback = null
                }
            }
        }

        override fun unregisterCallback(cb: IRnsCallback?) {
            callback = null
        }

        override fun enableDiscovery() {
            executor.execute { doEnableDiscovery() }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                executor.execute { doStop() }
            }
            ACTION_START -> {
                if (!tryStartForeground("Starting...")) return START_NOT_STICKY
            }
            else -> {
                // Null intent = system restart via START_STICKY
                val savedConfig = readSavedConfig()
                if (savedConfig == null) {
                    // Intentional stop — config was deleted, don't restart
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (!tryStartForeground("Restarting...")) return START_NOT_STICKY
                executor.execute { doStart(savedConfig) }
            }
        }
        return START_STICKY
    }

    private fun tryStartForeground(status: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(status),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification(status))
            }
            true
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Log.e(TAG, "Cannot start foreground service", e)
            stopSelf()
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error starting foreground", e)
            stopSelf()
            false
        }
    }

    override fun onDestroy() {
        pollingFuture?.cancel(false)
        executor.shutdown()
        releaseWakeLock()
        super.onDestroy()
    }

    // --- Work executed on the single-thread executor ---

    private fun doStart(configJson: String) {
        if (currentState == "running" || currentState == "starting") return
        currentState = "starting"
        currentError = null
        updateAndBroadcast(ServiceSnapshot(state = "starting"))
        acquireWakeLock()

        try {
            val json = JSONObject(configJson)
            val configIni = json.getString("configIni")
            val rnodeConfigs = parseRnodeConfigs(json.optJSONArray("rnodeInterfaces"))

            // Save for START_STICKY recovery
            saveSavedConfig(configJson)

            val b = ReticulumBinding(filesDir.absolutePath, this)
            b.initialize(configIni, rnodeConfigs)
            binding = b

            currentState = "running"
            currentError = null
            updateNotification("Running")
            startPolling()
            pollAndBroadcast()

            Log.i(TAG, "Transport started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start transport", e)
            currentState = "error"
            currentError = friendlyErrorMessage(e)
            updateNotification("Error: $currentError")
            releaseWakeLock()
            updateAndBroadcast(
                ServiceSnapshot(state = "error", error = currentError),
            )
        }
    }

    private fun doStop() {
        pollingFuture?.cancel(false)
        pollingFuture = null

        // Delete saved config so START_STICKY doesn't auto-restart
        deleteSavedConfig()

        try {
            binding?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown", e)
        }
        binding = null
        releaseWakeLock()

        // Remove the foreground notification before killing the process
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)

        Log.i(TAG, "Transport stopped, exiting process")
        System.exit(0)
    }

    private fun doEnableDiscovery() {
        val b = binding ?: return
        val success = b.enableDiscovery()
        discoveryEnabled = success
        if (success) {
            Log.i(TAG, "Interface discovery enabled")
            pollAndBroadcast()
        }
    }

    private fun startPolling() {
        pollingFuture?.cancel(false)
        pollingFuture = executor.scheduleAtFixedRate({
            try {
                pollAndBroadcast()
            } catch (e: Exception) {
                Log.w(TAG, "Error during stats poll", e)
            }
        }, STATS_POLL_INTERVAL_MS, STATS_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }

    private fun pollAndBroadcast() {
        val b = binding
        val snapshot = ServiceSnapshot(
            state = currentState,
            error = currentError,
            identity = b?.getTransportIdentityHash(),
            connectedToSharedInstance = b?.isConnectedToSharedInstance() ?: false,
            interfaces = b?.getInterfaceStats() ?: emptyList(),
            pathTable = b?.getPathTable() ?: emptyList(),
            announceQueue = b?.getAnnounceTable() ?: emptyList(),
            discoveredInterfaces = if (discoveryEnabled) {
                b?.getDiscoveredInterfaces() ?: emptyList()
            } else {
                emptyList()
            },
            discoveryEnabled = discoveryEnabled,
        )
        updateAndBroadcast(snapshot)
    }

    private fun updateAndBroadcast(snapshot: ServiceSnapshot) {
        val json = snapshot.toJson()
        cachedSnapshot = json
        val cb = callback
        if (cb != null) {
            try {
                cb.onUpdate(json)
            } catch (e: Exception) {
                Log.w(TAG, "Callback dead, removing", e)
                callback = null
            }
        }
    }

    // --- Config persistence for START_STICKY ---

    private fun saveSavedConfig(json: String) {
        try {
            File(filesDir, SAVED_CONFIG_FILE).writeText(json)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save config", e)
        }
    }

    private fun readSavedConfig(): String? {
        return try {
            val f = File(filesDir, SAVED_CONFIG_FILE)
            if (f.exists()) f.readText() else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read saved config", e)
            null
        }
    }

    private fun deleteSavedConfig() {
        try {
            File(filesDir, SAVED_CONFIG_FILE).delete()
        } catch (_: Exception) {}
    }

    // --- RNode config JSON parsing ---

    private fun parseRnodeConfigs(arr: JSONArray?): List<InterfaceConfig.RNodeInterface> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            try {
                val o = arr.getJSONObject(i)
                InterfaceConfig.RNodeInterface(
                    name = o.getString("name"),
                    enabled = true,
                    connectionMode = o.optString("connectionMode", "ble"),
                    targetDevice = o.optString("targetDevice", ""),
                    frequency = o.optLong("frequency", 0),
                    bandwidth = o.optInt("bandwidth", 0),
                    spreadingFactor = o.optInt("spreadingFactor", 0),
                    codingRate = o.optInt("codingRate", 0),
                    txPower = o.optInt("txPower", 0),
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse RNode config", e)
                null
            }
        }
    }

    // --- Notification ---

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reticulum",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Reticulum network stack service"
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
            .setContentTitle("Reticulum")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_notification, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, createNotification(status))
    }

    // --- Wake lock ---

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

    // --- Error formatting ---

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
}

sealed class ServiceState {
    data object Stopped : ServiceState()
    data object Starting : ServiceState()
    data object Running : ServiceState()
    data object Stopping : ServiceState()
    data class Error(val message: String) : ServiceState()
}
