package tech.torlando.rns.service

import org.json.JSONArray
import org.json.JSONObject
import tech.torlando.rns.data.AnnounceEntry
import tech.torlando.rns.data.DiscoveredInterface
import tech.torlando.rns.data.InterfaceStats
import tech.torlando.rns.data.PathEntry

data class ServiceSnapshot(
    val state: String,
    val error: String? = null,
    val identity: String? = null,
    val connectedToSharedInstance: Boolean = false,
    val interfaces: List<InterfaceStats> = emptyList(),
    val pathTable: List<PathEntry> = emptyList(),
    val announceQueue: List<AnnounceEntry> = emptyList(),
    val discoveredInterfaces: List<DiscoveredInterface> = emptyList(),
    val discoveryEnabled: Boolean = false,
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("state", state)
        obj.put("error", error ?: JSONObject.NULL)
        obj.put("identity", identity ?: JSONObject.NULL)
        obj.put("connectedToSharedInstance", connectedToSharedInstance)
        obj.put("discoveryEnabled", discoveryEnabled)

        val ifArr = JSONArray()
        for (i in interfaces) {
            ifArr.put(JSONObject().apply {
                put("name", i.name)
                put("displayName", i.displayName)
                put("rxb", i.rxb)
                put("txb", i.txb)
                put("online", i.online)
                put("reconnecting", i.reconnecting)
                put("neverConnected", i.neverConnected)
                put("detached", i.detached)
                put("type", i.type)
                put("parentInterfaceName", i.parentInterfaceName ?: JSONObject.NULL)
            })
        }
        obj.put("interfaces", ifArr)

        val ptArr = JSONArray()
        for (p in pathTable) {
            ptArr.put(JSONObject().apply {
                put("hash", p.hash)
                put("via", p.via)
                put("hops", p.hops)
                put("expires", p.expires)
                put("interfaceName", p.interfaceName)
            })
        }
        obj.put("pathTable", ptArr)

        val aqArr = JSONArray()
        for (a in announceQueue) {
            aqArr.put(JSONObject().apply {
                put("hash", a.hash)
                put("hops", a.hops)
                put("timestamp", a.timestamp)
                put("interfaceName", a.interfaceName)
            })
        }
        obj.put("announceQueue", aqArr)

        val diArr = JSONArray()
        for (d in discoveredInterfaces) {
            diArr.put(JSONObject().apply {
                put("name", d.name)
                put("type", d.type)
                put("status", d.status)
                put("transport", d.transport)
                put("transportId", d.transportId)
                put("hops", d.hops)
                put("stampValue", d.stampValue)
                put("reachableOn", d.reachableOn ?: JSONObject.NULL)
                put("port", d.port ?: JSONObject.NULL)
                put("latitude", d.latitude ?: JSONObject.NULL)
                put("longitude", d.longitude ?: JSONObject.NULL)
                put("height", d.height ?: JSONObject.NULL)
                put("ifacNetname", d.ifacNetname ?: JSONObject.NULL)
                put("ifacNetkey", d.ifacNetkey ?: JSONObject.NULL)
                put("lastHeard", d.lastHeard)
                put("discovered", d.discovered)
                put("heardCount", d.heardCount)
                put("configEntry", d.configEntry ?: JSONObject.NULL)
            })
        }
        obj.put("discoveredInterfaces", diArr)

        return obj.toString()
    }

    companion object {
        fun fromJson(json: String): ServiceSnapshot {
            val obj = JSONObject(json)
            return ServiceSnapshot(
                state = obj.optString("state", "stopped"),
                error = obj.stringOrNull("error"),
                identity = obj.stringOrNull("identity"),
                connectedToSharedInstance = obj.optBoolean("connectedToSharedInstance", false),
                discoveryEnabled = obj.optBoolean("discoveryEnabled", false),
                interfaces = parseInterfaces(obj.optJSONArray("interfaces")),
                pathTable = parsePathTable(obj.optJSONArray("pathTable")),
                announceQueue = parseAnnounceQueue(obj.optJSONArray("announceQueue")),
                discoveredInterfaces = parseDiscoveredInterfaces(obj.optJSONArray("discoveredInterfaces")),
            )
        }

        private fun JSONObject.stringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key, "").takeIf { it.isNotEmpty() }

        private fun parseInterfaces(arr: JSONArray?): List<InterfaceStats> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                InterfaceStats(
                    name = o.getString("name"),
                    displayName = o.optString("displayName", o.getString("name")),
                    rxb = o.optLong("rxb", 0),
                    txb = o.optLong("txb", 0),
                    online = o.optBoolean("online", false),
                    reconnecting = o.optBoolean("reconnecting", false),
                    neverConnected = o.optBoolean("neverConnected", false),
                    detached = o.optBoolean("detached", false),
                    type = o.optString("type", ""),
                    parentInterfaceName = o.stringOrNull("parentInterfaceName"),
                )
            }
        }

        private fun parsePathTable(arr: JSONArray?): List<PathEntry> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PathEntry(
                    hash = o.getString("hash"),
                    via = o.optString("via", ""),
                    hops = o.optInt("hops", 0),
                    expires = o.optLong("expires", 0),
                    interfaceName = o.optString("interfaceName", ""),
                )
            }
        }

        private fun parseAnnounceQueue(arr: JSONArray?): List<AnnounceEntry> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AnnounceEntry(
                    hash = o.getString("hash"),
                    hops = o.optInt("hops", 0),
                    timestamp = o.optDouble("timestamp", 0.0),
                    interfaceName = o.optString("interfaceName", ""),
                )
            }
        }

        private fun parseDiscoveredInterfaces(arr: JSONArray?): List<DiscoveredInterface> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DiscoveredInterface(
                    name = o.optString("name", "Unknown"),
                    type = o.optString("type", ""),
                    status = o.optString("status", "unknown"),
                    transport = o.optBoolean("transport", false),
                    transportId = o.optString("transportId", ""),
                    hops = o.optInt("hops", 0),
                    stampValue = o.optInt("stampValue", 0),
                    reachableOn = o.stringOrNull("reachableOn"),
                    port = if (o.isNull("port")) null else o.optInt("port"),
                    latitude = if (o.isNull("latitude")) null else o.optDouble("latitude"),
                    longitude = if (o.isNull("longitude")) null else o.optDouble("longitude"),
                    height = if (o.isNull("height")) null else o.optDouble("height"),
                    ifacNetname = o.stringOrNull("ifacNetname"),
                    ifacNetkey = o.stringOrNull("ifacNetkey"),
                    lastHeard = o.optDouble("lastHeard", 0.0),
                    discovered = o.optDouble("discovered", 0.0),
                    heardCount = o.optInt("heardCount", 0),
                    configEntry = o.stringOrNull("configEntry"),
                )
            }
        }
    }
}
