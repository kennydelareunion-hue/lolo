package com.termux.devcenter.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.termux.devcenter.data.omniroute.OmniRouteConfig

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val omniConfig by viewModel.omniConfig.collectAsState()
    val testResult by viewModel.testResult.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Réglages",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        omniConfig?.let { ConfigForm(it, testResult, viewModel::saveAndTest) }
    }
}

@Composable
private fun ConfigForm(
    initial: OmniRouteConfig,
    testResult: String?,
    onSaveAndTest: (OmniRouteConfig) -> Unit
) {
    var url by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var command by remember { mutableStateOf(initial.startCommand) }
    var showKey by remember { mutableStateOf(false) }
    var mcpUrl by remember { mutableStateOf(initial.mcpUrl) }
    var mcpEnabled by remember { mutableStateOf(initial.mcpEnabled) }
    var confirm by remember { mutableStateOf(initial.confirmCommands) }
    var mcpCommand by remember { mutableStateOf(initial.mcpStartCommand) }

    SettingsSection(title = "OmniRoute") {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL d'OmniRoute") },
            placeholder = { Text(OmniRouteConfig.DEFAULT_BASE_URL) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("Clé API OmniRoute") },
            supportingText = { Text("Onglet OmniRoute › icône clé (API Manager) › créer une clé.") },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showKey) "Masquer" else "Afficher"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            label = { Text("Commande de démarrage (Termux)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    SettingsSection(title = "Omni-Exec (serveur MCP)") {
        SwitchRow("Donner les outils Omni-Exec à Claude", mcpEnabled) { mcpEnabled = it }
        SwitchRow("Demander confirmation avant chaque commande", confirm) { confirm = it }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = mcpUrl,
            onValueChange = { mcpUrl = it },
            label = { Text("URL MCP d'Omni-Exec") },
            placeholder = { Text(OmniRouteConfig.DEFAULT_MCP_URL) },
            supportingText = { Text("Endpoint HTTP JSON-RPC du serveur (souvent …/mcp).") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = mcpCommand,
            onValueChange = { mcpCommand = it },
            label = { Text("Commande de démarrage (Termux)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = {
            onSaveAndTest(
                initial.copy(
                    baseUrl = url,
                    apiKey = apiKey,
                    startCommand = command,
                    mcpUrl = mcpUrl.ifBlank { OmniRouteConfig.DEFAULT_MCP_URL },
                    mcpEnabled = mcpEnabled,
                    confirmCommands = confirm,
                    mcpStartCommand = mcpCommand
                )
            )
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Save, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Enregistrer et tester")
    }
    testResult?.let {
        Spacer(modifier = Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(text = it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}
