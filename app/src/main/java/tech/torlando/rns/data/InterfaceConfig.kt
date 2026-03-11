package tech.torlando.rns.data

import org.json.JSONArray

sealed class InterfaceConfig {
    abstract val name: String
    abstract val enabled: Boolean
    abstract val networkName: String
    abstract val passphrase: String
    abstract val ifacSize: Int
    abstract val interfaceMode: String

    companion object {
        fun fromJson(json: String): List<InterfaceConfig> {
            if (json.isBlank() || json == "[]") return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.getJSONObject(i)
                    val name = obj.optString("name", "")
                    val enabled = obj.optBoolean("enabled", true)
                    val networkName = obj.optString("network_name", "")
                    val passphrase = obj.optString("passphrase", "")
                    val ifacSize = obj.optInt("ifac_size", 0)
                    val interfaceMode = obj.optString("interface_mode", "full")
                    when (obj.getString("type")) {
                        "tcp_client" -> TcpClient(
                            name = name.ifEmpty { "TCP Client" },
                            enabled = enabled,
                            networkName = networkName, passphrase = passphrase,
                            ifacSize = ifacSize, interfaceMode = interfaceMode,
                            targetHost = obj.optString("target_host", ""),
                            targetPort = obj.optInt("target_port", 4242),
                        )
                        "tcp_server" -> TcpServer(
                            name = name.ifEmpty { "TCP Server" },
                            enabled = enabled,
                            networkName = networkName, passphrase = passphrase,
                            ifacSize = ifacSize, interfaceMode = interfaceMode,
                            listenIp = obj.optString("listen_ip", "0.0.0.0"),
                            listenPort = obj.optInt("listen_port", 4242),
                        )
                        "auto" -> AutoInterface(
                            name = name.ifEmpty { "Auto Interface" },
                            enabled = enabled,
                            networkName = networkName, passphrase = passphrase,
                            ifacSize = ifacSize, interfaceMode = interfaceMode,
                            groupId = obj.optString("group_id", ""),
                            discoveryScope = obj.optString("discovery_scope", "link"),
                        )
                        "udp" -> UdpInterface(
                            name = name.ifEmpty { "UDP Interface" },
                            enabled = enabled,
                            networkName = networkName, passphrase = passphrase,
                            ifacSize = ifacSize, interfaceMode = interfaceMode,
                            listenIp = obj.optString("listen_ip", "0.0.0.0"),
                            listenPort = obj.optInt("listen_port", 0),
                            forwardIp = obj.optString("forward_ip", ""),
                            forwardPort = obj.optInt("forward_port", 0),
                        )
                        "i2p" -> I2PInterface(
                            name = name.ifEmpty { "I2P Interface" },
                            enabled = enabled,
                            networkName = networkName, passphrase = passphrase,
                            ifacSize = ifacSize, interfaceMode = interfaceMode,
                            peers = obj.optString("peers", ""),
                            connectable = obj.optBoolean("connectable", false),
                        )
                        else -> null
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    data class TcpClient(
        override val name: String = "TCP Client",
        override val enabled: Boolean = true,
        override val networkName: String = "",
        override val passphrase: String = "",
        override val ifacSize: Int = 0,
        override val interfaceMode: String = "full",
        val targetHost: String = "",
        val targetPort: Int = 4242,
    ) : InterfaceConfig()

    data class TcpServer(
        override val name: String = "TCP Server",
        override val enabled: Boolean = true,
        override val networkName: String = "",
        override val passphrase: String = "",
        override val ifacSize: Int = 0,
        override val interfaceMode: String = "full",
        val listenIp: String = "0.0.0.0",
        val listenPort: Int = 4242,
    ) : InterfaceConfig()

    data class AutoInterface(
        override val name: String = "Auto Interface",
        override val enabled: Boolean = true,
        override val networkName: String = "",
        override val passphrase: String = "",
        override val ifacSize: Int = 0,
        override val interfaceMode: String = "full",
        val groupId: String = "",
        val discoveryScope: String = "link",
    ) : InterfaceConfig()

    data class UdpInterface(
        override val name: String = "UDP Interface",
        override val enabled: Boolean = true,
        override val networkName: String = "",
        override val passphrase: String = "",
        override val ifacSize: Int = 0,
        override val interfaceMode: String = "full",
        val listenIp: String = "0.0.0.0",
        val listenPort: Int = 0,
        val forwardIp: String = "",
        val forwardPort: Int = 0,
    ) : InterfaceConfig()

    data class I2PInterface(
        override val name: String = "I2P Interface",
        override val enabled: Boolean = true,
        override val networkName: String = "",
        override val passphrase: String = "",
        override val ifacSize: Int = 0,
        override val interfaceMode: String = "full",
        val peers: String = "",
        val connectable: Boolean = false,
    ) : InterfaceConfig()
}
