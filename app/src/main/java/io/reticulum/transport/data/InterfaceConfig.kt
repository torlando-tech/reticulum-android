package io.reticulum.transport.data

sealed class InterfaceConfig {
    abstract val name: String
    abstract val enabled: Boolean
    abstract val networkName: String
    abstract val passphrase: String
    abstract val ifacSize: Int
    abstract val interfaceMode: String

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
