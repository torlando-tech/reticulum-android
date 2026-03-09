package io.reticulum.transport.binding

import io.reticulum.transport.data.InterfaceConfig

object ConfigGenerator {

    fun generate(
        interfaces: List<InterfaceConfig>,
        transportEnabled: Boolean,
        shareInstance: Boolean,
    ): String = buildString {
        appendLine("[reticulum]")
        appendLine("  enable_transport = $transportEnabled")
        appendLine("  share_instance = $shareInstance")
        if (shareInstance) {
            appendLine("  shared_instance_type = tcp")
        }
        appendLine()
        appendLine("[logging]")
        appendLine("  loglevel = 4")
        appendLine()

        appendLine("[interfaces]")
        appendLine()

        if (interfaces.isEmpty()) {
            appendLine("  [[Default TCP Client]]")
            appendLine("    type = TCPClientInterface")
            appendLine("    enabled = no")
            appendLine("    target_host = 127.0.0.1")
            appendLine("    target_port = 4242")
            return@buildString
        }

        for (iface in interfaces) {
            if (!iface.enabled) continue

            when (iface) {
                is InterfaceConfig.TcpClient -> {
                    appendLine("  [[${iface.name}]]")
                    appendLine("    type = TCPClientInterface")
                    appendLine("    enabled = yes")
                    appendLine("    target_host = ${iface.targetHost}")
                    appendLine("    target_port = ${iface.targetPort}")
                }
                is InterfaceConfig.TcpServer -> {
                    appendLine("  [[${iface.name}]]")
                    appendLine("    type = TCPServerInterface")
                    appendLine("    enabled = yes")
                    appendLine("    listen_ip = ${iface.listenIp}")
                    appendLine("    listen_port = ${iface.listenPort}")
                }
                is InterfaceConfig.AutoInterface -> {
                    appendLine("  [[${iface.name}]]")
                    appendLine("    type = AutoInterface")
                    appendLine("    enabled = yes")
                    if (iface.groupId.isNotBlank()) {
                        appendLine("    group_id = ${iface.groupId}")
                    }
                    if (iface.discoveryScope != "link") {
                        appendLine("    discovery_scope = ${iface.discoveryScope}")
                    }
                }
                is InterfaceConfig.UdpInterface -> {
                    appendLine("  [[${iface.name}]]")
                    appendLine("    type = UDPInterface")
                    appendLine("    enabled = yes")
                    appendLine("    listen_ip = ${iface.listenIp}")
                    appendLine("    listen_port = ${iface.listenPort}")
                    appendLine("    forward_ip = ${iface.forwardIp}")
                    appendLine("    forward_port = ${iface.forwardPort}")
                }
                is InterfaceConfig.I2PInterface -> {
                    appendLine("  [[${iface.name}]]")
                    appendLine("    type = I2PInterface")
                    appendLine("    enabled = yes")
                    appendLine("    connectable = ${iface.connectable}")
                    if (iface.peers.isNotBlank()) {
                        appendLine("    peers =")
                        for (peer in iface.peers.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
                            appendLine("      $peer")
                        }
                    }
                }
            }

            // Common IFAC and mode fields
            appendCommonFields(iface)
            appendLine()
        }
    }

    private fun StringBuilder.appendCommonFields(iface: InterfaceConfig) {
        if (iface.networkName.isNotBlank()) {
            appendLine("    network_name = ${iface.networkName}")
        }
        if (iface.passphrase.isNotBlank()) {
            appendLine("    passphrase = ${iface.passphrase}")
        }
        if (iface.ifacSize > 0) {
            appendLine("    ifac_size = ${iface.ifacSize}")
        }
        if (iface.interfaceMode != "full") {
            appendLine("    interface_mode = ${iface.interfaceMode}")
        }
    }
}
