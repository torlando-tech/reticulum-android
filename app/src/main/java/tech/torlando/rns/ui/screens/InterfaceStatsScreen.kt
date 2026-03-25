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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tech.torlando.rns.data.InterfaceStatus
import tech.torlando.rns.stats.data.InterfaceHistoryPoint
import tech.torlando.rns.stats.data.formatBytes
import tech.torlando.rns.stats.ui.TrafficSpeedChart
import tech.torlando.rns.viewmodel.TransportViewModel

private val StatusOnline = Color(0xFF4CAF50)
private val StatusConnecting = Color(0xFFFFC107)
private val StatusOffline = Color(0xFF9E9E9E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterfaceStatsScreen(
    interfaceName: String,
    viewModel: TransportViewModel,
    onNavigateBack: () -> Unit,
) {
    val allStats by viewModel.interfaceStats.collectAsState()
    val allHistory by viewModel.interfaceHistory.collectAsState()

    val stats = allStats.firstOrNull { it.name == interfaceName }
    val history = allHistory[interfaceName] ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(interfaceName) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val statusText = when (stats?.status) {
                        InterfaceStatus.ONLINE -> "Online"
                        InterfaceStatus.CONNECTING -> "Connecting"
                        InterfaceStatus.RECONNECTING -> "Reconnecting"
                        InterfaceStatus.DETACHED -> "Detached"
                        else -> "Offline"
                    }
                    val statusColor = when (stats?.status) {
                        InterfaceStatus.ONLINE -> StatusOnline
                        InterfaceStatus.CONNECTING, InterfaceStatus.RECONNECTING -> StatusConnecting
                        else -> StatusOffline
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Circle,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    if (stats != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stats.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // RX/TX totals
            if (stats != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(
                        label = "Received",
                        value = formatBytes(stats.rxb),
                        icon = Icons.Filled.ArrowDownward,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "Transmitted",
                        value = formatBytes(stats.txb),
                        icon = Icons.Filled.ArrowUpward,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(16.dp))
            }

            // Traffic speed chart
            TrafficSpeedChart(
                history = history,
                title = "Traffic Speed",
                rxColor = MaterialTheme.colorScheme.primary,
                txColor = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
