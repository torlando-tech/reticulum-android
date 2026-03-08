package io.reticulum.transport.binding

import android.util.Log
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import io.reticulum.transport.data.DiscoveredInterface
import io.reticulum.transport.data.InterfaceConfig
import io.reticulum.transport.data.InterfaceStats
import java.io.File

class ReticulumBinding(private val storagePath: String) {

    companion object {
        private const val TAG = "ReticulumBinding"
    }

    private val py: Python = Python.getInstance()
    private var reticulum: PyObject? = null

    val isRunning: Boolean
        get() = reticulum != null

    fun initialize(
        transportEnabled: Boolean,
        shareInstance: Boolean,
        interfaces: List<InterfaceConfig>,
    ) {
        val configDir = File(storagePath, "reticulum")
        configDir.mkdirs()

        val configFile = File(configDir, "config")
        configFile.writeText(
            ConfigGenerator.generate(
                interfaces = interfaces,
                transportEnabled = transportEnabled,
                shareInstance = shareInstance,
            ),
        )

        Log.i(TAG, "Initializing RNS with config: ${configFile.absolutePath}")
        Log.d(TAG, "Config contents:\n${configFile.readText()}")

        val helper = py.getModule("rns_helper")
        reticulum = helper.callAttr("start", configDir.absolutePath)
        Log.i(TAG, "RNS initialized successfully")
    }

    fun shutdown() {
        try {
            val helper = py.getModule("rns_helper")
            helper.callAttr("stop")
            Log.i(TAG, "RNS shutdown complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error during RNS shutdown", e)
        } finally {
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
                    type = iface.get("TYPE")?.toString() ?: "",
                    parentInterfaceName = parentName,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting interface stats", e)
            emptyList()
        }
    }

    fun getPathTable(): List<io.reticulum.transport.data.PathEntry> {
        return try {
            val rns = py.getModule("RNS")
            val transport = rns.get("Transport") ?: return emptyList()
            val destinationTable = transport.get("destination_table")
            if (destinationTable == null || destinationTable.callAttr("__len__").toInt() == 0) {
                return emptyList()
            }

            val entries = mutableListOf<io.reticulum.transport.data.PathEntry>()
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
                        io.reticulum.transport.data.PathEntry(
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

    fun getAnnounceTable(): List<io.reticulum.transport.data.AnnounceEntry> {
        return try {
            val rns = py.getModule("RNS")
            val transport = rns.get("Transport") ?: return emptyList()
            val announceTable = transport.get("announce_table")
            if (announceTable == null || announceTable.callAttr("__len__").toInt() == 0) {
                return emptyList()
            }

            val entries = mutableListOf<io.reticulum.transport.data.AnnounceEntry>()
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
                        io.reticulum.transport.data.AnnounceEntry(
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
