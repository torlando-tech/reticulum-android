package tech.torlando.rns.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import tech.torlando.rns.data.InterfaceConfig
import tech.torlando.rns.data.InterfaceStats
import tech.torlando.rns.data.InterfaceStatus
import tech.torlando.rns.service.ServiceState
import tech.torlando.rns.viewmodel.TransportViewModel

private val StatusOnline = Color(0xFF4CAF50)
private val StatusConnecting = Color(0xFFFFC107)
private val StatusOffline = Color(0xFF9E9E9E)

@Composable
fun InterfacesScreen(
    viewModel: TransportViewModel,
    onNavigateToDiscovery: () -> Unit = {},
    onNavigateToRnodeWizard: () -> Unit = {},
    onNavigateToTcpClientWizard: () -> Unit = {},
    onNavigateToInterfaceStats: (String) -> Unit = {},
) {
    val interfaces by viewModel.interfaces.collectAsState()
    val liveStats by viewModel.interfaceStats.collectAsState()
    val serviceState by viewModel.serviceState.collectAsState()
    val pendingRestart by viewModel.pendingRestart.collectAsState()
    val isConnectedToSharedInstance by viewModel.isConnectedToSharedInstance.collectAsState()
    var showTypeSelector by remember { mutableStateOf(false) }
    var showTcpServerDialog by remember { mutableStateOf(false) }
    var showAutoDialog by remember { mutableStateOf(false) }
    var showUdpDialog by remember { mutableStateOf(false) }
    var showI2pDialog by remember { mutableStateOf(false) }
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    val isRunning = serviceState is ServiceState.Running

    // Build a lookup from live stats by raw name for matching configured interfaces
    val liveStatsByName = remember(liveStats) {
        liveStats.associateBy { it.name }
    }

    // Separate live interfaces into shared instance components and other internals
    val configuredNames = remember(interfaces) { interfaces.map { it.name }.toSet() }

    // Shared instance server (LocalServerInterface)
    val sharedInstanceServer = remember(liveStats) {
        liveStats.firstOrNull {
            it.name !in configuredNames &&
                (it.displayName.contains("Shared Instance", ignoreCase = true) ||
                    it.displayName.contains("LocalServer", ignoreCase = true) ||
                    it.type.contains("LocalServerInterface", ignoreCase = true))
        }
    }

    // Spawned clients connected to shared instance (LocalClientInterface)
    // RNS reports these as "LocalInterface[port]" with parent = shared instance name
    val sharedInstanceName = sharedInstanceServer?.name
    val spawnedClients = remember(liveStats, configuredNames, sharedInstanceName) {
        liveStats.filter {
            it.name !in configuredNames &&
                (it.parentInterfaceName == sharedInstanceName && sharedInstanceName != null ||
                    it.displayName.contains("LocalClient", ignoreCase = true) ||
                    it.displayName.contains("LocalInterface", ignoreCase = true) ||
                    it.type.contains("LocalClientInterface", ignoreCase = true))
        }
    }

    // Spawned peers grouped by parent interface name (e.g. AutoInterface peers)
    // Exclude local clients (already shown under shared instance)
    val localClientNames = remember(spawnedClients) { spawnedClients.map { it.name }.toSet() }
    val spawnedPeersByParent = remember(liveStats, configuredNames, localClientNames) {
        liveStats
            .filter {
                it.parentInterfaceName != null &&
                    it.name !in configuredNames &&
                    it.name !in localClientNames
            }
            .groupBy { it.parentInterfaceName!! }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val hasSharedInstance = isRunning && sharedInstanceServer != null
        val hasContent = interfaces.isNotEmpty() || hasSharedInstance

        if (!hasContent) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No interfaces configured",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Tap + to add an interface",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(Modifier.height(8.dp)) }

                // Service status card (starting/stopping/error)
                if (serviceState is ServiceState.Starting ||
                    serviceState is ServiceState.Stopping ||
                    serviceState is ServiceState.Error
                ) {
                    item {
                        StatusCard(serviceState)
                    }
                }

                // Shared instance info banner
                if (isRunning && isConnectedToSharedInstance) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        ) {
                            Text(
                                "Connected to another app's shared Reticulum instance. " +
                                    "Interfaces are managed by that instance.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }

                // Restart required banner
                if (pendingRestart) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Restart to apply changes",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { viewModel.restartService() }) {
                                    Text("Restart")
                                }
                            }
                        }
                    }
                }

                // Discover Interfaces card
                if (isRunning) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToDiscovery() },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CellTower,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(28.dp),
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Discover Interfaces",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Text(
                                        text = "Find interfaces announced by other nodes",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }
                }

                // Shared Instance section
                if (hasSharedInstance) {
                    item {
                        Text(
                            text = "Shared Instance",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    item {
                        SharedInstanceCard(
                            stats = sharedInstanceServer!!,
                            clientCount = spawnedClients.size,
                        )
                    }
                    // Spawned clients indented under shared instance
                    items(spawnedClients.size) { index ->
                        SpawnedClientCard(spawnedClients[index])
                    }
                }

                // Configured Interfaces section
                if (interfaces.isNotEmpty()) {
                    item {
                        Text(
                            text = "Configured Interfaces",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(
                                top = if (hasSharedInstance) 8.dp else 0.dp,
                                bottom = 4.dp,
                            ),
                        )
                    }
                    interfaces.forEachIndexed { index, config ->
                        item(key = "config-$index") {
                            val live = if (isRunning) liveStatsByName[config.name] else null
                            val peerCount = spawnedPeersByParent[config.name]?.size ?: 0
                            ConfiguredInterfaceCard(
                                config = config,
                                liveStats = live,
                                isRunning = isRunning,
                                onClick = { if (isRunning) onNavigateToInterfaceStats(config.name) },
                                onToggle = { viewModel.toggleInterfaceEnabled(index) },
                                onEdit = {
                                    editingIndex = index
                                    when (config) {
                                        is InterfaceConfig.TcpClient -> onNavigateToTcpClientWizard()
                                        is InterfaceConfig.TcpServer -> showTcpServerDialog = true
                                        is InterfaceConfig.AutoInterface -> showAutoDialog = true
                                        is InterfaceConfig.UdpInterface -> showUdpDialog = true
                                        is InterfaceConfig.I2PInterface -> showI2pDialog = true
                                        is InterfaceConfig.RNodeInterface -> onNavigateToRnodeWizard()
                                    }
                                },
                                onDelete = { pendingDeleteIndex = index },
                                peerCount = peerCount,
                            )
                        }
                        // Spawned peers under this interface
                        val peers = spawnedPeersByParent[config.name]
                        if (isRunning && peers != null) {
                            items(peers.size, key = { "peer-${config.name}-$it" }) { peerIdx ->
                                SpawnedPeerCard(peers[peerIdx])
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        FloatingActionButton(
            onClick = { showTypeSelector = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Interface")
        }
    }

    // Type selector dialog
    if (showTypeSelector) {
        InterfaceTypeSelectorDialog(
            onDismiss = { showTypeSelector = false },
            onTypeSelected = { type ->
                showTypeSelector = false
                when (type) {
                    "tcp_client" -> onNavigateToTcpClientWizard()
                    "tcp_server" -> showTcpServerDialog = true
                    "auto" -> showAutoDialog = true
                    "udp" -> showUdpDialog = true
                    "i2p" -> showI2pDialog = true
                    "rnode" -> onNavigateToRnodeWizard()
                }
            },
        )
    }

    if (showTcpServerDialog) {
        val existing = editingIndex?.let { interfaces.getOrNull(it) as? InterfaceConfig.TcpServer }
        TcpServerAddDialog(
            existing = existing,
            onDismiss = { showTcpServerDialog = false; editingIndex = null },
            onSave = { config ->
                if (editingIndex != null) viewModel.updateInterface(editingIndex!!, config)
                else viewModel.addInterface(config)
                showTcpServerDialog = false; editingIndex = null
            },
        )
    }

    if (showAutoDialog) {
        val existing = editingIndex?.let { interfaces.getOrNull(it) as? InterfaceConfig.AutoInterface }
        AutoAddDialog(
            existing = existing,
            onDismiss = { showAutoDialog = false; editingIndex = null },
            onSave = { config ->
                if (editingIndex != null) viewModel.updateInterface(editingIndex!!, config)
                else viewModel.addInterface(config)
                showAutoDialog = false; editingIndex = null
            },
        )
    }

    if (showUdpDialog) {
        val existing = editingIndex?.let { interfaces.getOrNull(it) as? InterfaceConfig.UdpInterface }
        UdpAddDialog(
            existing = existing,
            onDismiss = { showUdpDialog = false; editingIndex = null },
            onSave = { config ->
                if (editingIndex != null) viewModel.updateInterface(editingIndex!!, config)
                else viewModel.addInterface(config)
                showUdpDialog = false; editingIndex = null
            },
        )
    }

    if (showI2pDialog) {
        val existing = editingIndex?.let { interfaces.getOrNull(it) as? InterfaceConfig.I2PInterface }
        I2pAddDialog(
            existing = existing,
            onDismiss = { showI2pDialog = false; editingIndex = null },
            onSave = { config ->
                if (editingIndex != null) viewModel.updateInterface(editingIndex!!, config)
                else viewModel.addInterface(config)
                showI2pDialog = false; editingIndex = null
            },
        )
    }


    // Delete confirmation
    pendingDeleteIndex?.let { index ->
        val config = interfaces.getOrNull(index)
        if (config != null) {
            AlertDialog(
                onDismissRequest = { pendingDeleteIndex = null },
                title = { Text("Delete Interface") },
                text = { Text("Delete \"${config.name}\"? This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.removeInterface(index)
                        pendingDeleteIndex = null
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteIndex = null }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

// -- Configured interface card with live status --

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConfiguredInterfaceCard(
    config: InterfaceConfig,
    liveStats: InterfaceStats?,
    isRunning: Boolean,
    onClick: () -> Unit = {},
    onToggle: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit,
    peerCount: Int = 0,
) {
    val icon = when (config) {
        is InterfaceConfig.TcpClient -> Icons.Filled.Public
        is InterfaceConfig.TcpServer -> Icons.Filled.Dns
        is InterfaceConfig.AutoInterface -> Icons.Filled.Sensors
        is InterfaceConfig.UdpInterface -> Icons.Filled.Wifi
        is InterfaceConfig.I2PInterface -> Icons.Filled.Security
        is InterfaceConfig.RNodeInterface -> Icons.Filled.SettingsInputAntenna
    }
    val typeName = when (config) {
        is InterfaceConfig.TcpClient -> "TCP Client"
        is InterfaceConfig.TcpServer -> "TCP Server"
        is InterfaceConfig.AutoInterface -> "Auto Discovery"
        is InterfaceConfig.UdpInterface -> "UDP Interface"
        is InterfaceConfig.I2PInterface -> "I2P Network"
        is InterfaceConfig.RNodeInterface -> "RNode LoRa"
    }
    val target = when (config) {
        is InterfaceConfig.TcpClient -> "${config.targetHost}:${config.targetPort}"
        is InterfaceConfig.TcpServer -> "${config.listenIp}:${config.listenPort}"
        is InterfaceConfig.AutoInterface -> if (config.groupId.isNotBlank()) "Group: ${config.groupId}" else "Auto Discovery"
        is InterfaceConfig.UdpInterface -> "${config.listenIp}:${config.listenPort} -> ${config.forwardIp}:${config.forwardPort}"
        is InterfaceConfig.I2PInterface -> if (config.connectable) "Connectable" else "Client Only"
        is InterfaceConfig.RNodeInterface -> "${config.connectionMode} ${config.targetDevice}".trim().ifEmpty { "RNode" }
    }

    val statusText: String
    val statusColor: Color
    when {
        !config.enabled -> {
            statusText = "Disabled"
            statusColor = StatusOffline
        }
        !isRunning -> {
            statusText = ""
            statusColor = StatusOffline
        }
        liveStats != null -> {
            when (liveStats.status) {
                InterfaceStatus.ONLINE -> {
                    statusText = "Online"
                    statusColor = StatusOnline
                }
                InterfaceStatus.CONNECTING -> {
                    statusText = "Connecting"
                    statusColor = StatusConnecting
                }
                InterfaceStatus.RECONNECTING -> {
                    statusText = "Reconnecting"
                    statusColor = StatusConnecting
                }
                InterfaceStatus.DETACHED -> {
                    statusText = "Detached"
                    statusColor = StatusOffline
                }
                InterfaceStatus.OFFLINE -> {
                    statusText = "Offline"
                    statusColor = StatusOffline
                }
            }
        }
        else -> {
            statusText = "Offline"
            statusColor = StatusOffline
        }
    }

    val iconColor = when {
        !config.enabled -> StatusOffline
        liveStats?.status == InterfaceStatus.ONLINE -> StatusOnline
        liveStats?.status == InterfaceStatus.CONNECTING || liveStats?.status == InterfaceStatus.RECONNECTING -> StatusConnecting
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    var showContextMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showContextMenu = true
                    },
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(32.dp),
                )

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = config.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = typeName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = target,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (statusText.isNotEmpty()) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                        )
                    }
                    if (peerCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.People,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "$peerCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = config.enabled,
                        onCheckedChange = { onToggle() },
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    showContextMenu = false
                    onEdit()
                },
                leadingIcon = {
                    Icon(Icons.Default.Edit, contentDescription = null)
                },
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    showContextMenu = false
                    onDelete()
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
            )
        }
    }
}

// -- Shared Instance card (LocalServerInterface) --

@Composable
private fun SharedInstanceCard(stats: InterfaceStats, clientCount: Int) {
    val iconColor = if (stats.online) StatusOnline else StatusOffline

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Lan,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(32.dp),
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stats.name.ifEmpty { "Shared Instance" },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Local Server",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (stats.online) "Online" else "Offline",
                    style = MaterialTheme.typography.labelSmall,
                    color = iconColor,
                )
                if (clientCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.People,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "$clientCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// -- Spawned client card (LocalClientInterface, indented under Shared Instance) --

@Composable
private fun SpawnedClientCard(stats: InterfaceStats) {
    val iconColor = if (stats.online) StatusOnline else StatusOffline

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.People,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp),
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stats.displayName.ifEmpty { stats.name },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Local Client",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (stats.online) "Online" else "Offline",
                    style = MaterialTheme.typography.labelSmall,
                    color = iconColor,
                )
                Text(
                    text = "${formatBytes(stats.rxb)} / ${formatBytes(stats.txb)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// -- Spawned peer card (e.g. AutoInterface discovered peers, indented under parent) --

@Composable
private fun SpawnedPeerCard(stats: InterfaceStats) {
    val iconColor = if (stats.online) StatusOnline else StatusOffline

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Sensors,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp),
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stats.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Discovered Peer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (stats.online) "Online" else "Offline",
                    style = MaterialTheme.typography.labelSmall,
                    color = iconColor,
                )
                Text(
                    text = "${formatBytes(stats.rxb)} / ${formatBytes(stats.txb)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// -- Type selector dialog --

private data class InterfaceTypeOption(
    val key: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
)

private val interfaceTypes = listOf(
    InterfaceTypeOption("tcp_client", "TCP Client", "Connect to a remote transport node", Icons.Filled.Public),
    InterfaceTypeOption("tcp_server", "TCP Server", "Accept incoming connections from other nodes", Icons.Filled.Dns),
    InterfaceTypeOption("auto", "Auto Interface", "Discover peers on the local network", Icons.Filled.Sensors),
    InterfaceTypeOption("udp", "UDP Interface", "Broadcast or multicast UDP communication", Icons.Filled.Wifi),
    InterfaceTypeOption("i2p", "I2P Interface", "Anonymous communication via the I2P network", Icons.Filled.Security),
    InterfaceTypeOption("rnode", "RNode", "LoRa radio via Bluetooth or USB", Icons.Filled.SettingsInputAntenna),
)

@Composable
private fun InterfaceTypeSelectorDialog(
    onDismiss: () -> Unit,
    onTypeSelected: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Interface") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Select the type of interface to add:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                interfaceTypes.forEach { type ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTypeSelected(type.key) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = type.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = type.name,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = type.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// -- TCP Server dialog (add/edit) --

@Composable
private fun TcpServerAddDialog(
    existing: InterfaceConfig.TcpServer? = null,
    onDismiss: () -> Unit,
    onSave: (InterfaceConfig) -> Unit,
) {
    val isEditing = existing != null
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var listenIp by remember { mutableStateOf(existing?.listenIp ?: "0.0.0.0") }
    var port by remember { mutableStateOf(existing?.listenPort?.toString() ?: "4242") }
    val adv = rememberCommonIfacState(existing)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit TCP Server" else "Add TCP Server") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, placeholder = { Text("TCP Server") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = listenIp, onValueChange = { listenIp = it },
                    label = { Text("Listen IP") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = port, onValueChange = { port = it },
                    label = { Text("Port") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                AdvancedInterfaceFields(adv)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(InterfaceConfig.TcpServer(
                    name = name.ifBlank { "TCP Server" },
                    enabled = existing?.enabled ?: true,
                    listenIp = listenIp, listenPort = port.toIntOrNull() ?: 4242,
                    networkName = adv.networkName, passphrase = adv.passphrase,
                    ifacSize = adv.ifacSize.toIntOrNull() ?: 0, interfaceMode = adv.interfaceMode,
                ))
            }) { Text(if (isEditing) "Save" else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// -- Auto dialog (add/edit) --

@Composable
private fun AutoAddDialog(
    existing: InterfaceConfig.AutoInterface? = null,
    onDismiss: () -> Unit,
    onSave: (InterfaceConfig) -> Unit,
) {
    val isEditing = existing != null
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var groupId by remember { mutableStateOf(existing?.groupId ?: "") }
    var discoveryScope by remember { mutableStateOf(existing?.discoveryScope ?: "link") }
    val adv = rememberCommonIfacState(existing)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Auto Interface" else "Add Auto Interface") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, placeholder = { Text("Auto Interface") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = groupId, onValueChange = { groupId = it },
                    label = { Text("Group ID (optional)") }, placeholder = { Text("reticulum") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = discoveryScope, onValueChange = { discoveryScope = it },
                    label = { Text("Discovery Scope") }, placeholder = { Text("link") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    supportingText = { Text("link, admin, site, organisation, or global") },
                )
                AdvancedInterfaceFields(adv)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(InterfaceConfig.AutoInterface(
                    name = name.ifBlank { "Auto Interface" },
                    enabled = existing?.enabled ?: true,
                    groupId = groupId, discoveryScope = discoveryScope.ifBlank { "link" },
                    networkName = adv.networkName, passphrase = adv.passphrase,
                    ifacSize = adv.ifacSize.toIntOrNull() ?: 0, interfaceMode = adv.interfaceMode,
                ))
            }) { Text(if (isEditing) "Save" else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// -- UDP dialog (add/edit) --

@Composable
private fun UdpAddDialog(
    existing: InterfaceConfig.UdpInterface? = null,
    onDismiss: () -> Unit,
    onSave: (InterfaceConfig) -> Unit,
) {
    val isEditing = existing != null
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var listenIp by remember { mutableStateOf(existing?.listenIp ?: "0.0.0.0") }
    var listenPort by remember { mutableStateOf(existing?.listenPort?.toString() ?: "") }
    var forwardIp by remember { mutableStateOf(existing?.forwardIp ?: "") }
    var forwardPort by remember { mutableStateOf(existing?.forwardPort?.toString() ?: "") }
    val adv = rememberCommonIfacState(existing)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit UDP Interface" else "Add UDP Interface") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, placeholder = { Text("UDP Interface") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = listenIp, onValueChange = { listenIp = it },
                    label = { Text("Listen IP") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = listenPort, onValueChange = { listenPort = it },
                    label = { Text("Listen Port") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = forwardIp, onValueChange = { forwardIp = it },
                    label = { Text("Forward IP") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = forwardPort, onValueChange = { forwardPort = it },
                    label = { Text("Forward Port") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                AdvancedInterfaceFields(adv)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(InterfaceConfig.UdpInterface(
                        name = name.ifBlank { "UDP Interface" },
                        enabled = existing?.enabled ?: true,
                        listenIp = listenIp, listenPort = listenPort.toIntOrNull() ?: 0,
                        forwardIp = forwardIp, forwardPort = forwardPort.toIntOrNull() ?: 0,
                        networkName = adv.networkName, passphrase = adv.passphrase,
                        ifacSize = adv.ifacSize.toIntOrNull() ?: 0, interfaceMode = adv.interfaceMode,
                    ))
                },
                enabled = forwardIp.isNotBlank(),
            ) { Text(if (isEditing) "Save" else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// -- I2P dialog (add/edit) --

@Composable
private fun I2pAddDialog(
    existing: InterfaceConfig.I2PInterface? = null,
    onDismiss: () -> Unit,
    onSave: (InterfaceConfig) -> Unit,
) {
    val isEditing = existing != null
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var peers by remember { mutableStateOf(existing?.peers ?: "") }
    var connectable by remember { mutableStateOf(existing?.connectable ?: false) }
    val adv = rememberCommonIfacState(existing)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit I2P Interface" else "Add I2P Interface") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, placeholder = { Text("I2P Interface") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = peers, onValueChange = { peers = it },
                    label = { Text("Peers (comma-separated B32 addresses)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = false, minLines = 2,
                    supportingText = { Text("e.g. abc123...b32.i2p, def456...b32.i2p") },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Accept incoming connections", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = connectable, onCheckedChange = { connectable = it })
                }
                AdvancedInterfaceFields(adv)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(InterfaceConfig.I2PInterface(
                    name = name.ifBlank { "I2P Interface" },
                    enabled = existing?.enabled ?: true,
                    peers = peers, connectable = connectable,
                    networkName = adv.networkName, passphrase = adv.passphrase,
                    ifacSize = adv.ifacSize.toIntOrNull() ?: 0, interfaceMode = adv.interfaceMode,
                ))
            }) { Text(if (isEditing) "Save" else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}


// -- Common IFAC / mode fields for all interface dialogs --

private val interfaceModes = listOf("full", "access_point", "pointtopoint", "roaming", "boundary", "gateway")

data class CommonIfacState(
    var networkName: String = "",
    var passphrase: String = "",
    var ifacSize: String = "",
    var interfaceMode: String = "full",
)

@Composable
private fun rememberCommonIfacState(existing: InterfaceConfig? = null) = remember {
    if (existing != null) {
        CommonIfacState(
            networkName = existing.networkName,
            passphrase = existing.passphrase,
            ifacSize = if (existing.ifacSize > 0) existing.ifacSize.toString() else "",
            interfaceMode = existing.interfaceMode,
        )
    } else {
        CommonIfacState()
    }
}

@Composable
private fun AdvancedInterfaceFields(state: CommonIfacState) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Advanced",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.networkName,
                    onValueChange = { state.networkName = it },
                    label = { Text("Network Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.passphrase,
                    onValueChange = { state.passphrase = it },
                    label = { Text("Passphrase") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.ifacSize,
                    onValueChange = { state.ifacSize = it },
                    label = { Text("IFAC Size (bits)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("Minimum ${8 * 8} bits, or leave empty for default") },
                )
                // Interface mode selector
                var modeExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(
                        value = state.interfaceMode,
                        onValueChange = {},
                        label = { Text("Interface Mode") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { modeExpanded = true },
                        readOnly = true,
                        singleLine = true,
                    )
                    DropdownMenu(
                        expanded = modeExpanded,
                        onDismissRequest = { modeExpanded = false },
                    ) {
                        interfaceModes.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode) },
                                onClick = {
                                    state.interfaceMode = mode
                                    modeExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// -- Helpers --

private fun interfaceIcon(name: String): ImageVector = when {
    name.contains("Local", ignoreCase = true) -> Icons.Filled.Lan
    name.contains("TCP", ignoreCase = true) -> Icons.Filled.Public
    name.contains("Auto", ignoreCase = true) -> Icons.Filled.Sensors
    name.contains("UDP", ignoreCase = true) -> Icons.Filled.Wifi
    else -> Icons.Filled.Lan
}
