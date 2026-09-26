package com.termux.devcenter.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    var openCodeAddress by remember { mutableStateOf("127.0.0.1") }
    var openCodePort by remember { mutableStateOf("20128") }
    var bridgeAddress by remember { mutableStateOf("127.0.0.1") }
    var bridgePort by remember { mutableStateOf("8080") }
    var omniExecAddress by remember { mutableStateOf("127.0.0.1") }
    var omniExecPort by remember { mutableStateOf("20128") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Paramètres",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // OpenCode Server
        SettingsSection(title = "Serveur OpenCode") {
            OutlinedTextField(
                value = openCodeAddress,
                onValueChange = { openCodeAddress = it },
                label = { Text("Adresse") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = openCodePort,
                onValueChange = { openCodePort = it },
                label = { Text("Port") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bridge Server
        SettingsSection(title = "Bridge Termux") {
            OutlinedTextField(
                value = bridgeAddress,
                onValueChange = { bridgeAddress = it },
                label = { Text("Adresse Bridge") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = bridgePort,
                onValueChange = { bridgePort = it },
                label = { Text("Port Bridge") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Omni-Exec
        SettingsSection(title = "Omni-Exec MCP") {
            OutlinedTextField(
                value = omniExecAddress,
                onValueChange = { omniExecAddress = it },
                label = { Text("Adresse Omni-Exec") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = omniExecPort,
                onValueChange = { omniExecPort = it },
                label = { Text("Port Omni-Exec") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Actions
        Button(
            onClick = { /* Test connection */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Wifi, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Tester la connexion")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { /* Reconnect */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reconnecter")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Theme
        SettingsSection(title = "Apparence") {
            var selectedTheme by remember { mutableStateOf(0) }
            
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedTheme == 0,
                        onClick = { selectedTheme = 0 }
                    )
                    Text("Système")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedTheme == 1,
                        onClick = { selectedTheme = 1 }
                    )
                    Text("Clair")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedTheme == 2,
                        onClick = { selectedTheme = 2 }
                    )
                    Text("Sombre")
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}
