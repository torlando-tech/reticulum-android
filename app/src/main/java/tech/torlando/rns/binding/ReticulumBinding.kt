package tech.torlando.rns.binding

import android.content.Context
import android.util.Log
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import tech.torlando.rns.bridges.rnode.KotlinRNodeBridge
import tech.torlando.rns.bridges.usb.KotlinUSBBridge
import tech.torlando.rns.data.DiscoveredInterface
import tech.torlando.rns.data.InterfaceConfig
import tech.torlando.rns.data.InterfaceStats
import java.io.File

class ReticulumBinding(private val storagePath: String, private val context: Context) {

    companion object {
        private const val TAG = "ReticulumBinding"
    }

    private val py: Python = Python.getInstance()
    private var reticulum: PyObject? = null
    private var rnodeBridge: KotlinRNodeBridge? = null
    private var usbBridge: KotlinUSBBridge? = null

    val isRunning: Boolean
        get() = reticulum != null

    fun initialize(
        transportEnabled: Boolean,
        shareInstance: Boolean = true,
        sharedInstancePort: Int = 0,
        instanceControlPort: Int = 0,
        interfaces: List<InterfaceConfig>,
        publishBlackhole: Boolean = false,
        blackholeSources: String = "",
    ) {
        val configDir = File(storagePath, "reticulum")
        configDir.mkdirs()

        val configFile = File(configDir, "config")
        configFile.writeText(
            ConfigGenerator.generate(
                interfaces = interfaces,
                transportEnabled = transportEnabled,
                shareInstance = shareInstance,
                sharedInstancePort = sharedInstancePort,
                instanceControlPort = instanceControlPort,
                publishBlackhole = publishBlackhole,
                blackholeSources = blackholeSources,
            ),
        )

        Log.i(TAG, "Initializing RNS with config: ${configFile.absolutePath}")
        Log.d(TAG, "Config contents:\n${configFile.readText()}")

        val helper = py.getModule("rns_helper")
        reticulum = helper.callAttr("start", configDir.absolutePath)
        Log.i(TAG, "RNS initialized successfully")

        // Set up RNode bridges and create interfaces for any RNode configs
        val rnodeConfigs = interfaces.filterIsInstance<InterfaceConfig.RNodeInterface>()
            .filter { it.enabled }
        Log.i(TAG, "Found ${rnodeConfigs.size} enabled RNode configs out of ${interfaces.size} total interfaces")
        if (rnodeConfigs.isNotEmpty()) {
            setupRNodeBridges(helper, rnodeConfigs)
        }
    }

    private fun setupRNodeBridges(
        helper: PyObject,
        rnodeConfigs: List<InterfaceConfig.RNodeInterface>,
    ) {
        try {
            // Create and set Kotlin bridges
            val bridge = KotlinRNodeBridge(context)
            rnodeBridge = bridge
            helper.callAttr("set_rnode_bridge", bridge)
            Log.i(TAG, "RNode bridge set")

            val usb = KotlinUSBBridge(context)
            usbBridge = usb
            helper.callAttr("set_usb_bridge", usb)
            Log.i(TAG, "USB bridge set")

            // Create an RNode interface for each config
            for (config in rnodeConfigs) {
                try {
                    helper.callAttr(
                        "create_rnode_interface",
                        config.name,
                        config.connectionMode,
                        config.targetDevice,
                        config.frequency,
                        config.bandwidth,
                        config.spreadingFactor,
                        config.codingRate,
                        config.txPower,
                    )
                    Log.i(TAG, "Created RNode interface: ${config.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create RNode interface: ${config.name}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up RNode bridges", e)
        }
    }

    fun shutdown() {
        try {
            val helper = py.getModule("rns_helper")
            helper.callAttr("stop")
            Log.i(TAG, "RNS shutdown complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error during RNS shutdown", e)
        } finally {
            try {
                rnodeBridge?.shutdown()
            } catch (_: Exception) {}
            try {
                usbBridge?.shutdown()
            } catch (_: Exception) {}
            rnodeBridge = null
            usbBridge = null
            reticulum = null
        }
    }

    fun getTransportIdentityHash(): String? {
        return try {
            val rns = py.getModule("RNS")
            val transport = rns.get("Transport") ?: return null
            val identity = transport.get("identity") ?: return null
            identity.callAttr("__str__")?.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting transport identity", e)
            null
        }
    }

    fun isConnectedToSharedInstance(): Boolean {
        return try {
            val helper = py.getModule("rns_helper")
            helper.callAttr("is_connected_to_shared_instance").toBoolean()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking shared instance status", e)
            false
        }
    }

    fun isTransportEnabled(): Boolean {
        return try {
            val rns = py.getModule("RNS")
            val transport = rns.get("Transport") ?: return false
            transport.get("identity") != null
        } catch (e: Exception) {
            false
        }
    }

    fun getInterfaceStats(): List<InterfaceStats> {
        return try {
            val rns = py.getModule("RNS")
            val transport = rns.get("Transport") ?: return emptyList()
            val interfaces = transport.get("interfaces")?.asList() ?: return emptyList()

            interfaces.map { iface ->
                val rawName = iface.get("name")?.toString() ?: ""
                val displayName = iface.callAttr("__str__").toString()
                val parentName = try {
                    iface.get("parent_interface")?.get("name")?.toString()
                } catch (_: Exception) { null }
                InterfaceStats(
                    name = rawName,
                    displayName = displayName,
                    rxb = iface.get("rxb")?.toLong() ?: 0,
                    txb = iface.get("txb")?.toLong() ?: 0,
                    online = iface.get("online")?.toBoolean() ?: false,
                    reconnecting = try { iface.get("reconnecting")?.toBoolean() ?: false } catch (_: Exception) { false },
                    neverConnected = try { iface.get("never_connected")?.toBoolean() ?: false } catch (_: Exception) { false },
                    detached = try { iface.get("detached")?.toBoolean() ?: false } catch (_: Exception) { false },
                    type = iface.get("TYPE")?.toString() ?: "",
                    parentInterfaceName = parentName,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting interface stats", e)
            emptyList()
        }
    }

    fun getPathTable(): List<tech.torlando.rns.data.PathEntry> {
        return try {
            val rns = py.getModule("RNS")
            val transport = rns.get("Transport") ?: return emptyList()
            val destinationTable = transport.get("destination_table")
            if (destinationTable == null || destinationTable.callAttr("__len__").toInt() == 0) {
                return emptyList()
            }

            val entries = mutableListOf<tech.torlando.rns.data.PathEntry>()
            val builtins = py.getBuiltins()
            val items = builtins.callAttr("list", destinationTable.callAttr("items")).asList()
            for (item in items) {
                try {
                    val itemList = item.asList()
                    val hashBytes = itemList[0]
                    val entry = itemList[1].asList()
                    val timestamp = entry[0].toDouble()
                    val via = entry[1]?.toString() ?: ""
                    val hops = entry[3]?.toInt() ?: 0
                    val expires = entry[4]?.toDouble()?.toLong() ?: 0L
                    val ifaceName = entry[5]?.callAttr("__str__")?.toString() ?: "Unknown"

                    entries.add(
                        tech.torlando.rns.data.PathEntry(
                            hash = hashBytes.callAttr("hex").toString(),
                            via = via,
                            hops = hops,
                            expires = expires,
                            interfaceName = ifaceName,
                        ),
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing path entry", e)
                }
            }
            entries
        } catch (e: Exception) {
            Log.e(TAG, "Error getting path table", e)
            emptyList()
        }
    }

    fun getAnnounceTable(): List<tech.torlando.rns.data.AnnounceEntry> {
        return try {
            val rns = py.getModule("RNS")
            val transport = rns.get("Transport") ?: return emptyList()
            val announceTable = transport.get("announce_table")
            if (announceTable == null || announceTable.callAttr("__len__").toInt() == 0) {
                return emptyList()
            }

            val entries = mutableListOf<tech.torlando.rns.data.AnnounceEntry>()
            val builtins = py.getBuiltins()
            val items = builtins.callAttr("list", announceTable.callAttr("items")).asList()
            for (item in items) {
                try {
                    val itemList = item.asList()
                    val hashBytes = itemList[0]
                    val entry = itemList[1].asList()
                    val timestamp = entry[0].toDouble()
                    val hops = entry[4]?.toInt() ?: 0
                    val ifaceName = entry[6]?.callAttr("__str__")?.toString() ?: "Unknown"

                    entries.add(
                        tech.torlando.rns.data.AnnounceEntry(
                            hash = hashBytes.callAttr("hex").toString(),
                            hops = hops,
                            timestamp = timestamp,
                            interfaceName = ifaceName,
                        ),
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing announce entry", e)
                }
            }
            entries
        } catch (e: Exception) {
            Log.e(TAG, "Error getting announce table", e)
            emptyList()
        }
    }

    fun enableDiscovery(): Boolean {
        return try {
            val helper = py.getModule("rns_helper")
            helper.callAttr("enable_discovery").toBoolean()
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling discovery", e)
            false
        }
    }

    fun getDiscoveredInterfaces(): List<DiscoveredInterface> {
        return try {
            val helper = py.getModule("rns_helper")
            val pyList = helper.callAttr("list_discovered")
            val builtins = py.getBuiltins()
            val items = builtins.callAttr("list", pyList).asList()

            items.mapNotNull { info ->
                try {
                    DiscoveredInterface(
                        name = info.callAttr("get", "name", "Unknown")?.toString() ?: "Unknown",
                        type = info.callAttr("get", "type", "")?.toString() ?: "",
                        status = info.callAttr("get", "status", "unknown")?.toString() ?: "unknown",
                        transport = info.callAttr("get", "transport", false)?.toBoolean() ?: false,
                        transportId = info.callAttr("get", "transport_id", "")?.toString() ?: "",
                        hops = info.callAttr("get", "hops", 0)?.toInt() ?: 0,
                        stampValue = info.callAttr("get", "value", 0)?.toInt() ?: 0,
                        reachableOn = info.callAttr("get", "reachable_on")?.toString(),
                        port = try { info.callAttr("get", "port")?.toInt() } catch (_: Exception) { null },
                        latitude = try { info.callAttr("get", "latitude")?.toDouble() } catch (_: Exception) { null },
                        longitude = try { info.callAttr("get", "longitude")?.toDouble() } catch (_: Exception) { null },
                        height = try { info.callAttr("get", "height")?.toDouble() } catch (_: Exception) { null },
                        ifacNetname = info.callAttr("get", "ifac_netname")?.toString(),
                        ifacNetkey = info.callAttr("get", "ifac_netkey")?.toString(),
                        lastHeard = info.callAttr("get", "last_heard", 0.0)?.toDouble() ?: 0.0,
                        discovered = info.callAttr("get", "discovered", 0.0)?.toDouble() ?: 0.0,
                        heardCount = info.callAttr("get", "heard_count", 0)?.toInt() ?: 0,
                        configEntry = info.callAttr("get", "config_entry")?.toString(),
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing discovered interface", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting discovered interfaces", e)
            emptyList()
        }
    }
}
