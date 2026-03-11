package tech.torlando.rns.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import tech.torlando.rns.viewmodel.TransportViewModel

@Composable
fun SettingsScreen(viewModel: TransportViewModel) {
    val transportEnabled by viewModel.transportEnabled.collectAsState()
    val shareInstance by viewModel.shareInstance.collectAsState()
    val sharedInstancePort by viewModel.sharedInstancePort.collectAsState()
    val instanceControlPort by viewModel.instanceControlPort.collectAsState()
    val publishBlackhole by viewModel.publishBlackhole.collectAsState()
    val blackholeSources by viewModel.blackholeSources.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(16.dp))

        Text("Transport", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        SettingsCard(
            title = "Enable Transport",
            description = "Act as a transport node, forwarding packets for the network",
            checked = transportEnabled,
            onCheckedChange = { viewModel.setTransportEnabled(it) },
        )

        Spacer(Modifier.height(16.dp))

        Text("Instance Sharing", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        SettingsCard(
            title = "Share Instance",
            description = "Allow other apps on this device to use this Reticulum instance",
            checked = shareInstance,
            onCheckedChange = { viewModel.setShareInstance(it) },
        )

        if (shareInstance) {
            Spacer(Modifier.height(8.dp))

            SettingsTextField(
                label = "Shared Instance Port",
                value = if (sharedInstancePort > 0) sharedInstancePort.toString() else "",
                onValueChange = { viewModel.setSharedInstancePort(it.toIntOrNull() ?: 0) },
                placeholder = "37428",
                description = "Port for other apps to connect to this instance",
                keyboardType = KeyboardType.Number,
            )

            Spacer(Modifier.height(8.dp))

            SettingsTextField(
                label = "Instance Control Port",
                value = if (instanceControlPort > 0) instanceControlPort.toString() else "",
                onValueChange = { viewModel.setInstanceControlPort(it.toIntOrNull() ?: 0) },
                placeholder = "37429",
                description = "Port for instance control channel",
                keyboardType = KeyboardType.Number,
            )
        }

        Spacer(Modifier.height(16.dp))

        Text("Blackhole Management", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        SettingsCard(
            title = "Publish Blackhole List",
            description = "Publish this node's blocklist so subscribers can fetch it",
            checked = publishBlackhole,
            onCheckedChange = { viewModel.setPublishBlackhole(it) },
        )

        Spacer(Modifier.height(8.dp))

        SettingsTextField(
            label = "Blackhole Sources",
            value = blackholeSources,
            onValueChange = { viewModel.setBlackholeSources(it) },
            placeholder = "ab1c2d3e4f..., 9f8e7d6c5b...",
            description = "Comma-separated transport identity hashes to subscribe to for blocklist updates",
        )

        Spacer(Modifier.height(24.dp))

        Text("Info", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                InfoRow("App Version", "0.1.0")
                InfoRow("RNS Backend", "Python via Chaquopy")
                InfoRow("Note", "Changes take effect on next service restart")
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    description: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
