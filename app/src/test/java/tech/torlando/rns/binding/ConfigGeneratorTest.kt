package tech.torlando.rns.binding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.torlando.rns.data.InterfaceConfig

class ConfigGeneratorTest {

    @Test
    fun `empty interface list produces disabled default`() {
        val config = ConfigGenerator.generate(
            interfaces = emptyList(),
            transportEnabled = false,
        )
        assertTrue(config.contains("enable_transport = false"))
        assertTrue(config.contains("[[Default TCP Client]]"))
        assertTrue(config.contains("enabled = no"))
    }

    @Test
    fun `transport enabled flag propagates`() {
        val config = ConfigGenerator.generate(
            interfaces = emptyList(),
            transportEnabled = true,
        )
        assertTrue(config.contains("enable_transport = true"))
    }

    @Test
    fun `shared instance defaults to tcp mode`() {
        val config = ConfigGenerator.generate(
            interfaces = emptyList(),
            transportEnabled = false,
            shareInstance = true,
        )
        assertTrue(config.contains("share_instance = Yes"))
        assertTrue(config.contains("shared_instance_type = tcp"))
    }

    @Test
    fun `shared instance disabled`() {
        val config = ConfigGenerator.generate(
            interfaces = emptyList(),
            transportEnabled = false,
            shareInstance = false,
        )
        assertTrue(config.contains("share_instance = No"))
        assertFalse(config.contains("shared_instance_type"))
    }

    @Test
    fun `shared instance ports included when nonzero`() {
        val config = ConfigGenerator.generate(
            interfaces = emptyList(),
            transportEnabled = false,
            shareInstance = true,
            sharedInstancePort = 37428,
            instanceControlPort = 37429,
        )
        assertTrue(config.contains("shared_instance_port = 37428"))
        assertTrue(config.contains("instance_control_port = 37429"))
    }

    @Test
    fun `shared instance ports omitted when zero`() {
        val config = ConfigGenerator.generate(
            interfaces = emptyList(),
            transportEnabled = false,
            shareInstance = true,
            sharedInstancePort = 0,
            instanceControlPort = 0,
        )
        assertFalse(config.contains("shared_instance_port"))
        assertFalse(config.contains("instance_control_port"))
    }

    @Test
    fun `tcp client interface`() {
        val ifaces = listOf(
            InterfaceConfig.TcpClient(
                name = "Test TCP",
                enabled = true,
                targetHost = "example.com",
                targetPort = 4242,
            ),
        )
        val config = ConfigGenerator.generate(ifaces, transportEnabled = false)
        assertTrue(config.contains("[[Test TCP]]"))
        assertTrue(config.contains("type = TCPClientInterface"))
        assertTrue(config.contains("enabled = yes"))
        assertTrue(config.contains("target_host = example.com"))
        assertTrue(config.contains("target_port = 4242"))
    }

    @Test
    fun `tcp client with socks proxy`() {
        val ifaces = listOf(
            InterfaceConfig.TcpClient(
                name = "Tor TCP",
                enabled = true,
                targetHost = "example.com",
                targetPort = 4242,
                socksProxyEnabled = true,
                socksProxyHost = "127.0.0.1",
                socksProxyPort = 9050,
            ),
        )
        val config = ConfigGenerator.generate(ifaces, transportEnabled = false)
        assertTrue(config.contains("socks_host = 127.0.0.1"))
        assertTrue(config.contains("socks_port = 9050"))
    }

    @Test
    fun `tcp client without socks proxy omits socks fields`() {
        val ifaces = listOf(
            InterfaceConfig.TcpClient(
                name = "Plain TCP",
                enabled = true,
                targetHost = "example.com",
                targetPort = 4242,
                socksProxyEnabled = false,
            ),
        )
        val config = ConfigGenerator.generate(ifaces, transportEnabled = false)
        assertFalse(config.contains("socks_host"))
        assertFalse(config.contains("socks_port"))
    }

    @Test
    fun `tcp client bootstrap only`() {
        val ifaces = listOf(
            InterfaceConfig.TcpClient(
                name = "Bootstrap",
                enabled = true,
                targetHost = "example.com",
                targetPort = 4242,
                bootstrapOnly = true,
            ),
        )
        val config = ConfigGenerator.generate(ifaces, transportEnabled = false)
        assertTrue(config.contains("bootstrap_only = true"))
    }

    @Test
    fun `tcp server interface`() {
        val ifaces = listOf(
            InterfaceConfig.TcpServer(
                name = "My Server",
                enabled = true,
                listenIp = "0.0.0.0",
                listenPort = 4242,
            ),
        )
        val config = ConfigGenerator.generate(ifaces, transportEnabled = false)
        assertTrue(config.contains("[[My Server]]"))
        assertTrue(config.contains("type = TCPServerInterface"))
        assertTrue(config.contains("listen_ip = 0.0.0.0"))
        assertTrue(config.contains("listen_port = 4242"))
    }

    @Test
    fun `auto interface with defaults`() {
        val ifaces = listOf(
            InterfaceConfig.AutoInterface(name = "Auto", enabled = true),
        )
        val config = ConfigGenerator.generate(ifaces, transportEnabled = false)
        assertTrue(config.contains("type = AutoInterface"))
        // Default discovery_scope is "link", should be omitted
        assertFalse(config.contains("discovery_scope"))
        // Empty group_id should be omitted
        assertFalse(config.contains("group_id"))
    }

    @Test
    fun `auto interface with custom group and scope`() {
        val ifaces = listOf(
            InterfaceConfig.AutoInterface(
                name = "Auto Custom",
                enabled = true,
                groupId = "mygroup",
                discoveryScope = "admin",
            ),
        )
        val config = ConfigGenerator.generate(ifaces, transportEnabled = false)
        assertTrue(config.contains("group_id = mygroup"))
        assertTrue(config.contains("discovery_scope = admin"))
    }

    @Test
    fun `udp interface`() {
        val ifaces = listOf(
            InterfaceConfig.UdpInterface(
                name = "UDP",
                enabled = true,
                listenIp = "0.0.0.0",
                listenPort = 5555,
                forwardIp = "192.168.1.255",
                forwardPort = 5555,
            ),
        )
        val config = ConfigGenerator.generate(ifaces, transportEnabled = false)
        assertTrue(config.contains("type = UDPInterface"))
        assertTrue(config.contains("listen_port = 5555"))
        assertTrue(config.contains("forward_ip = 192.168.1.255"))
        assertTrue(config.contains("forward_port = 5555"))
    }

    @Test
    fun `i2p interface with peers`() {
        val ifaces = listOf(
            InterfaceConfig.I2PInterface(
                name = "I2P",
                enabled = true,
                peers = "peer1.b32.i2p, peer2.b32.i2p",
                connectable = true,
            ),
        )
        val config = ConfigGenerator.generate(ifaces, transportEnabled = false)
        assertTrue(config.contains("type = I2PInterface"))
        assertTrue(config.contains("connectable = true"))
        assertTrue(config.contains("peers ="))
        assertTrue(config.contains("peer1.b32.i2p"))
        assertTrue(config.contains("peer2.b32.i2p"))
    }

    @Test
    fun `disabled interfaces are skipped`() {
        val ifaces = listOf(
            InterfaceConfig.TcpClient(
                name = "Disabled",
                enabled = false,
                targetHost = "example.com",
                targetPort = 4242,
            ),
            InterfaceConfig.TcpClient(
                name = "Enabled",
                enabled = true,
                targetHost = "other.com",
                targetPort = 4243,
            ),
        )
        val config = ConfigGenerator.generate(ifaces, transportEnabled = false)
        assertFalse(config.contains("[[Disabled]]"))
        assertTrue(config.contains("[[Enabled]]"))
    }

    @Test
    fun `rnode interfaces are skipped in config`() {
        val ifaces = listOf(
            InterfaceConfig.RNodeInterface(
                name = "My RNode",
                enabled = true,
                frequency = 867_200_000,
                bandwidth = 125000,
                spreadingFactor = 8,
                codingRate = 5,
                txPower = 14,
            ),
        )
        val config = ConfigGenerator.generate(ifaces, transportEnabled = false)
        // RNode is handled via Kotlin bridge, not config file
        assertFalse(config.contains("[[My RNode]]"))
    }

    @Test
    fun `common ifac fields included when set`() {
        val ifaces = listOf(
            InterfaceConfig.TcpClient(
                name = "IFAC Test",
                enabled = true,
                targetHost = "example.com",
                targetPort = 4242,
                networkName = "testnet",
                passphrase = "secret",
                ifacSize = 16,
                interfaceMode = "gateway",
            ),
        )
        val config = ConfigGenerator.generate(ifaces, transportEnabled = false)
        assertTrue(config.contains("network_name = testnet"))
        assertTrue(config.contains("passphrase = secret"))
        assertTrue(config.contains("ifac_size = 16"))
        assertTrue(config.contains("interface_mode = gateway"))
    }

    @Test
    fun `common ifac fields omitted when default`() {
        val ifaces = listOf(
            InterfaceConfig.TcpClient(
                name = "Plain",
                enabled = true,
                targetHost = "example.com",
                targetPort = 4242,
                networkName = "",
                passphrase = "",
                ifacSize = 0,
                interfaceMode = "full",
            ),
        )
        val config = ConfigGenerator.generate(ifaces, transportEnabled = false)
        assertFalse(config.contains("network_name"))
        assertFalse(config.contains("passphrase"))
        assertFalse(config.contains("ifac_size"))
        assertFalse(config.contains("interface_mode"))
    }

    @Test
    fun `blackhole settings`() {
        val config = ConfigGenerator.generate(
            interfaces = emptyList(),
            transportEnabled = true,
            publishBlackhole = true,
            blackholeSources = "deadbeef",
        )
        assertTrue(config.contains("publish_blackhole = Yes"))
        assertTrue(config.contains("blackhole_sources = deadbeef"))
    }

    @Test
    fun `blackhole disabled by default`() {
        val config = ConfigGenerator.generate(
            interfaces = emptyList(),
            transportEnabled = false,
        )
        assertTrue(config.contains("publish_blackhole = No"))
        assertFalse(config.contains("blackhole_sources"))
    }

    @Test
    fun `multiple interfaces produce multiple sections`() {
        val ifaces = listOf(
            InterfaceConfig.TcpClient(name = "TCP 1", enabled = true, targetHost = "a.com", targetPort = 4242),
            InterfaceConfig.TcpServer(name = "Server 1", enabled = true, listenIp = "0.0.0.0", listenPort = 4243),
            InterfaceConfig.AutoInterface(name = "Auto 1", enabled = true),
        )
        val config = ConfigGenerator.generate(ifaces, transportEnabled = false)
        assertTrue(config.contains("[[TCP 1]]"))
        assertTrue(config.contains("[[Server 1]]"))
        assertTrue(config.contains("[[Auto 1]]"))
        assertEquals(1, config.lines().count { it.trim() == "[interfaces]" })
    }
}
