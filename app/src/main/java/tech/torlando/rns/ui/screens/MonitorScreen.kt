package tech.torlando.rns.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import tech.torlando.rns.data.InterfaceStats
import tech.torlando.rns.data.PathEntry
import tech.torlando.rns.service.ServiceState
import tech.torlando.rns.viewmodel.TransportViewModel

@Composable
fun MonitorScreen(viewModel: TransportViewModel) {
    val serviceState by viewModel.serviceState.collectAsState()
    val interfaceStats by viewModel.interfaceStats.collectAsState()
    val pathTable by viewModel.pathTable.collectAsState()
    val announceTable by viewModel.announceTable.collectAsState()

    if (serviceState !is ServiceState.Running) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Service not running",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Start the transport service to see stats",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("Interfaces", style = MaterialTheme.typography.titleMedium)
        }

        if (interfaceStats.isEmpty()) {
            item {
                Text(
                    "No interfaces active",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(interfaceStats) { stats ->
                InterfaceStatsCard(stats)
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("Path Table (${pathTable.size})", style = MaterialTheme.typography.titleMedium)
        }

        if (pathTable.isEmpty()) {
            item {
                Text(
                    "No paths known",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(pathTable) { entry ->
                PathEntryCard(entry)
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("Announce Table (${announceTable.size})", style = MaterialTheme.typography.titleMedium)
        }

        if (announceTable.isEmpty()) {
            item {
                Text(
                    "No announces received",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(announceTable) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = entry.hash.take(16) + "...",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text = "Hops: ${entry.hops} | Via: ${entry.interfaceName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun InterfaceStatsCard(stats: InterfaceStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Circle,
                    contentDescription = null,
                    modifier = Modifier.size(8.dp),
                    tint = if (stats.online) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                )
                Text(
                    text = stats.name,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("RX: ${formatBytes(stats.rxb)}", style = MaterialTheme.typography.bodySmall)
                Text("TX: ${formatBytes(stats.txb)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PathEntryCard(entry: PathEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = entry.hash.take(16) + "...",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "Hops: ${entry.hops} | Via: ${entry.interfaceName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
