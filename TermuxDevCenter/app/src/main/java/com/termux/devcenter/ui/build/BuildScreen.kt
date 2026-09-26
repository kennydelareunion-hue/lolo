package com.termux.devcenter.ui.build

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun BuildScreen(viewModel: BuildViewModel = viewModel()) {
    val buildStatus by viewModel.buildStatus.collectAsState()
    val buildLogs by viewModel.buildLogs.collectAsState()
    val isBuilding by viewModel.isBuilding.collectAsState()
    var selectedProject by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Compilation APK",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Project selection
        OutlinedTextField(
            value = selectedProject,
            onValueChange = { selectedProject = it },
            label = { Text("Chemin du projet") },
            placeholder = { Text("Ex: /data/data/com.termux/files/home/MonProjet") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBuilding
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Build button
        Button(
            onClick = { 
                if (selectedProject.isNotBlank()) {
                    viewModel.startBuild(selectedProject)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBuilding && selectedProject.isNotBlank()
        ) {
            Icon(
                imageVector = if (isBuilding) Icons.Default.HourglassEmpty else Icons.Default.Build,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isBuilding) "Compilation en cours..." else "Lancer la compilation")
        }

        if (isBuilding) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Build status
        if (buildStatus.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        buildStatus.contains("succès", ignoreCase = true) -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                        buildStatus.contains("échec", ignoreCase = true) -> Color(0xFFF44336).copy(alpha = 0.1f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            buildStatus.contains("succès", ignoreCase = true) -> Icons.Default.CheckCircle
                            buildStatus.contains("échec", ignoreCase = true) -> Icons.Default.Error
                            else -> Icons.Default.Info
                        },
                        contentDescription = null,
                        tint = when {
                            buildStatus.contains("succès", ignoreCase = true) -> Color(0xFF4CAF50)
                            buildStatus.contains("échec", ignoreCase = true) -> Color(0xFFF44336)
                            else -> Color.Gray
                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = buildStatus)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Build logs
        Text(
            text = "Logs de compilation",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            )
        ) {
            Text(
                text = buildLogs.ifEmpty { "Les logs s'afficheront ici..." },
                fontFamily = FontFamily.Monospace,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}
