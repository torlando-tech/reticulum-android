package io.reticulum.transport.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.reticulum.transport.service.ServiceState
import io.reticulum.transport.viewmodel.TransportViewModel

@Composable
fun HomeScreen(viewModel: TransportViewModel) {
    val serviceState by viewModel.serviceState.collectAsState()
    val transportIdentity by viewModel.transportIdentity.collectAsState()
    val interfaceStats by viewModel.interfaceStats.collectAsState()
    val pathTable by viewModel.pathTable.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Reticulum Transport",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(Modifier.height(24.dp))

        // Status indicator
        StatusCard(serviceState)

        Spacer(Modifier.height(16.dp))

        // Start/Stop button
        when (serviceState) {
            is ServiceState.Stopped, is ServiceState.Error -> {
                Button(
                    onClick = { viewModel.startService() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text("  Start Transport")
                }
            }
            is ServiceState.Running -> {
                Button(
                    onClick = { viewModel.stopService() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Text("  Stop Transport")
                }
            }
            is ServiceState.Starting -> {
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                ) {
                    Text("Starting...")
                }
            }
            is ServiceState.Stopping -> {
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                ) {
                    Text("Stopping...")
                }
            }
        }

        // Transport Identity
        if (transportIdentity != null) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Transport Identity", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = transportIdentity ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                }
            }
        }

        // Quick stats when running
        if (serviceState is ServiceState.Running) {
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard(
                    title = "Interfaces",
                    value = interfaceStats.size.toString(),
                    subtitle = "${interfaceStats.count { it.online }} online",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    title = "Paths",
                    value = pathTable.size.toString(),
                    subtitle = "known",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val totalRx = interfaceStats.sumOf { it.rxb }
                val totalTx = interfaceStats.sumOf { it.txb }
                StatCard(
                    title = "RX",
                    value = formatBytes(totalRx),
                    subtitle = "received",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    title = "TX",
                    value = formatBytes(totalTx),
                    subtitle = "sent",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatusCard(state: ServiceState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                is ServiceState.Running -> MaterialTheme.colorScheme.primaryContainer
                is ServiceState.Error -> MaterialTheme.colorScheme.errorContainer
                is ServiceState.Starting, is ServiceState.Stopping -> MaterialTheme.colorScheme.tertiaryContainer
                is ServiceState.Stopped -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.Circle,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = when (state) {
                    is ServiceState.Running -> Color(0xFF4CAF50)
                    is ServiceState.Error -> Color(0xFFF44336)
                    is ServiceState.Starting, is ServiceState.Stopping -> Color(0xFFFF9800)
                    is ServiceState.Stopped -> Color(0xFF9E9E9E)
                },
            )
            Column {
                Text(
                    text = when (state) {
                        is ServiceState.Running -> "Running"
                        is ServiceState.Error -> "Error"
                        is ServiceState.Starting -> "Starting"
                        is ServiceState.Stopping -> "Stopping"
                        is ServiceState.Stopped -> "Stopped"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state is ServiceState.Error) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = title, style = MaterialTheme.typography.labelSmall)
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}
