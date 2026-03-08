package io.reticulum.transport.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.reticulum.transport.data.DiscoveredInterface
import io.reticulum.transport.service.ServiceState
import io.reticulum.transport.viewmodel.TransportViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val StatusAvailable = Color(0xFF4CAF50)
private val StatusUnknown = Color(0xFFFFA726)
private val StatusStale = Color(0xFF9E9E9E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveredInterfacesScreen(
    viewModel: TransportViewModel,
    onNavigateBack: () -> Unit,
) {
    val discovered by viewModel.discoveredInterfaces.collectAsState()
    val discoveryEnabled by viewModel.discoveryEnabled.collectAsState()
    val serviceState by viewModel.serviceState.collectAsState()
    val isRunning = serviceState is ServiceState.Running

    // Enable discovery when screen opens
    LaunchedEffect(isRunning) {
        if (isRunning && !discoveryEnabled) {
            viewModel.enableDiscovery()
        }
    }

    // Refresh on entry
    LaunchedEffect(discoveryEnabled) {
        if (discoveryEnabled) {
            viewModel.refreshDiscoveredInterfaces()
        }
    }

    val availableCount = discovered.count { it.status == "available" }
    val unknownCount = discovered.count { it.status == "unknown" }
    val staleCount = discovered.count { it.status == "stale" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Discovered Interfaces") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshDiscoveredInterfaces() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        if (!isRunning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Transport must be running to discover interfaces",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)) }

                // Status summary card
                item {
                    StatusSummaryCard(
                        total = discovered.size,
                        available = availableCount,
                        unknown = unknownCount,
                        stale = staleCount,
                    )
                }

                if (discovered.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.CellTower,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = if (discoveryEnabled) "Listening for interface announcements..." else "Enabling discovery...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "Discovered interfaces will appear here",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(discovered, key = { it.transportId + it.name }) { iface ->
                        DiscoveredInterfaceCard(iface)
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun StatusSummaryCard(
    total: Int,
    available: Int,
    unknown: Int,
    stale: Int,
) {
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
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatusCount("Total", total, MaterialTheme.colorScheme.onSurfaceVariant)
            StatusCount("Available", available, StatusAvailable)
            StatusCount("Unknown", unknown, StatusUnknown)
            StatusCount("Stale", stale, StatusStale)
        }
    }
}

@Composable
private fun StatusCount(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleLarge,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DiscoveredInterfaceCard(iface: DiscoveredInterface) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val statusColor = when (iface.status) {
        "available" -> StatusAvailable
        "unknown" -> StatusUnknown
        else -> StatusStale
    }

    val icon = when {
        iface.type.contains("TCP", ignoreCase = true) -> Icons.Filled.Public
        iface.type.contains("I2P", ignoreCase = true) -> Icons.Filled.Security
        else -> Icons.Filled.CellTower
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header row: icon, name, status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = iface.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = iface.type,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Status + Transport badges
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (iface.transport) {
                        StatusBadge("Transport", MaterialTheme.colorScheme.primary)
                    }
                    StatusBadge(
                        iface.status.replaceFirstChar { it.uppercase() },
                        statusColor,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DetailItem("Hops", "${iface.hops}")
                DetailItem("Stamp", "${iface.stampValue}")
                DetailItem("Heard", "${iface.heardCount}x")
            }

            // Transport ID
            if (iface.transportId.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "ID: ${iface.transportId.take(16)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Reachable on
            if (!iface.reachableOn.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                val endpoint = if (iface.port != null) "${iface.reachableOn}:${iface.port}" else iface.reachableOn
                Text(
                    text = "Endpoint: $endpoint",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Location
            if (iface.latitude != null && iface.longitude != null &&
                iface.latitude != 0.0 && iface.longitude != 0.0
            ) {
                Spacer(Modifier.height(4.dp))
                val loc = buildString {
                    append("Location: %.4f, %.4f".format(iface.latitude, iface.longitude))
                    if (iface.height != null && iface.height != 0.0) {
                        append(" (%.0fm)".format(iface.height))
                    }
                }
                Text(
                    text = loc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Last heard
            Spacer(Modifier.height(4.dp))
            val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }
            Text(
                text = "Last heard: ${dateFormat.format(Date((iface.lastHeard * 1000).toLong()))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Copy config entry button
            if (!iface.configEntry.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Surface(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(iface.configEntry))
                            Toast.makeText(context, "Config entry copied", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Copy Endpoint",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
