package com.termux.devcenter.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.termux.devcenter.data.model.ServerStatus

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel()) {
    val serverStatus by viewModel.serverStatus.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Termux Dev Center",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Status cards
        StatusSection(serverStatus)

        Spacer(modifier = Modifier.height(24.dp))

        // Quick actions grid
        Text(
            text = "Actions rapides",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        QuickActionsGrid()
    }
}

@Composable
fun StatusSection(status: ServerStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            StatusRow("OpenCode", status.openCodeConnected)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            StatusRow("Serveur Bridge", status.bridgeConnected)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            StatusRow("Omni-Exec", status.omniExecConnected)
        }
    }
}

@Composable
fun StatusRow(label: String, isConnected: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Circle,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isConnected) "Connecté" else "Déconnecté",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
        }
    }
}

@Composable
fun QuickActionsGrid() {
    val actions = listOf(
        QuickAction("OpenCode", Icons.Default.Code, Color(0xFF2196F3)),
        QuickAction("Projets", Icons.Default.Folder, Color(0xFF4CAF50)),
        QuickAction("Fichiers", Icons.Default.InsertDriveFile, Color(0xFFFF9800)),
        QuickAction("Terminal", Icons.Default.Terminal, Color(0xFF9C27B0)),
        QuickAction("Compilation", Icons.Default.Build, Color(0xFFF44336)),
        QuickAction("Sessions", Icons.Default.History, Color(0xFF00BCD4))
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(actions) { action ->
            QuickActionCard(action)
        }
    }
}

@Composable
fun QuickActionCard(action: QuickAction) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = action.color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.title,
                modifier = Modifier.size(32.dp),
                tint = action.color
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = action.title,
                style = MaterialTheme.typography.bodyMedium,
                color = action.color
            )
        }
    }
}

data class QuickAction(
    val title: String,
    val icon: ImageVector,
    val color: Color
)
