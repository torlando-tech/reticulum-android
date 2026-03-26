package tech.torlando.rns.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import tech.torlando.rns.data.PathEntry
import tech.torlando.rns.service.ServiceState
import tech.torlando.rns.viewmodel.TransportViewModel

@Composable
fun MonitorScreen(viewModel: TransportViewModel) {
    val serviceState by viewModel.serviceState.collectAsState()
    val pathTable by viewModel.pathTable.collectAsState()
    val announceTable by viewModel.announceTable.collectAsState()
    var showAllPaths by remember { mutableStateOf(false) }
    var showAllAnnounces by remember { mutableStateOf(false) }

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
            val pathPreviewCount = 5
            val visiblePaths = if (showAllPaths) pathTable else pathTable.take(pathPreviewCount)
            items(visiblePaths) { entry ->
                PathEntryCard(entry)
            }
            if (pathTable.size > pathPreviewCount) {
                item {
                    TextButton(
                        onClick = { showAllPaths = !showAllPaths },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (showAllPaths) "Show less" else "Show all ${pathTable.size} paths")
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("Announce Queue (${announceTable.size})", style = MaterialTheme.typography.titleMedium)
        }

        if (announceTable.isEmpty()) {
            item {
                Text(
                    "No announces pending",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val announcePreviewCount = 5
            val visibleAnnounces = if (showAllAnnounces) announceTable else announceTable.take(announcePreviewCount)
            items(visibleAnnounces) { entry ->
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
            if (announceTable.size > announcePreviewCount) {
                item {
                    TextButton(
                        onClick = { showAllAnnounces = !showAllAnnounces },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (showAllAnnounces) "Show less" else "Show all ${announceTable.size} announces")
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
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
