package com.termux.devcenter.ui.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.termux.devcenter.data.model.ServerStatus
import com.termux.devcenter.data.termux.TermuxLauncher

private val Green = Color(0xFF4CAF50)
private val Orange = Color(0xFFFF9800)
private val Red = Color(0xFFF44336)
private val Grey = Color(0xFF9E9E9E)

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    onNavigate: (String) -> Unit = {}
) {
    val status by viewModel.serverStatus.collectAsState()
    val startMessage by viewModel.startMessage.collectAsState()
    val context = LocalContext.current
    var pendingStart by remember { mutableStateOf<Service?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val service = pendingStart
        if (granted && service != null) viewModel.start(service) else viewModel.onPermissionDenied()
    }
    val start: (Service) -> Unit = { service ->
        if (TermuxLauncher.hasPermission(context)) viewModel.start(service)
        else {
            pendingStart = service
            permissionLauncher.launch(TermuxLauncher.PERMISSION)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Services", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                val omniColor = when {
                    !status.checked -> Grey
                    status.omniRouteConnected -> Green
                    status.omniRouteAuthRequired -> Orange
                    else -> Red
                }
                ServiceRow(
                    label = "OmniRoute",
                    color = omniColor,
                    state = when (omniColor) {
                        Green -> "Connecté"
                        Orange -> "Clé API requise"
                        Grey -> "…"
                        else -> "Arrêté"
                    },
                    detail = when {
                        status.omniRouteConnected && status.omniRouteModelCount == 0 ->
                            "Aucun modèle : connectez un fournisseur (Kiro…) dans l'onglet OmniRoute."
                        status.omniRouteConnected ->
                            "${status.omniRouteModelCount} modèle(s) connecté(s) : ${status.omniRouteProCount} Pro · " +
                                "${status.omniRouteFreeCount} gratuit(s)"
                        status.omniRouteAuthRequired ->
                            "OmniRoute tourne. Créez une clé API (onglet OmniRoute › icône clé) et collez-la dans Réglages."
                        else -> status.omniRouteMessage
                    },
                    action = when (omniColor) {
                        Orange -> "Réglages" to { onNavigate("settings") }
                        Red -> "Démarrer" to { start(Service.OMNIROUTE) }
                        else -> null
                    }
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                val execColor = when {
                    !status.checked -> Grey
                    status.omniExecConnected -> Green
                    else -> Red
                }
                ServiceRow(
                    label = "Omni-Exec (MCP)",
                    color = execColor,
                    state = when (execColor) {
                        Green -> "Connecté"
                        Grey -> "…"
                        else -> "Déconnecté"
                    },
                    detail = if (status.omniExecConnected) "${status.omniExecToolCount} outil(s) pour Claude"
                    else status.omniExecMessage,
                    action = if (execColor == Red) "Démarrer" to { start(Service.OMNI_EXEC) } else null
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                ServiceRow(
                    label = "Bridge Termux",
                    color = if (!status.checked) Grey else if (status.bridgeConnected) Green else Red,
                    state = if (status.bridgeConnected) "Connecté" else "Déconnecté",
                    detail = null,
                    action = null
                )

                startMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = viewModel::refreshStatus) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Actualiser")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Actions rapides",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        QuickActionsGrid(onNavigate)
    }
}

@Composable
private fun ServiceRow(
    label: String,
    color: Color,
    state: String,
    detail: String?,
    action: Pair<String, () -> Unit>?
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Circle, contentDescription = null, modifier = Modifier.size(12.dp), tint = color)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = state, style = MaterialTheme.typography.bodyMedium, color = color)
            }
        }
        detail?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.let { (text, onClick) ->
            Spacer(Modifier.height(4.dp))
            FilledTonalButton(onClick = onClick) { Text(text, maxLines = 1) }
        }
    }
}

@Composable
fun QuickActionsGrid(onNavigate: (String) -> Unit = {}) {
    val actions = listOf(
        QuickAction("Chat IA", Icons.Default.Chat, Color(0xFF2196F3), "opencode"),
        QuickAction("OmniRoute", Icons.Default.Hub, Color(0xFF3F51B5), "omniroute"),
        QuickAction("Projets", Icons.Default.Folder, Color(0xFF4CAF50), "projects"),
        QuickAction("Fichiers", Icons.Default.InsertDriveFile, Color(0xFFFF9800), "files"),
        QuickAction("Terminal", Icons.Default.Terminal, Color(0xFF9C27B0), "terminal"),
        QuickAction("Compilation", Icons.Default.Build, Color(0xFFF44336), "build"),
        QuickAction("Sessions", Icons.Default.History, Color(0xFF00BCD4), "sessions"),
        QuickAction("Réglages", Icons.Default.Settings, Color(0xFF607D8B), "settings"),
        QuickAction("Hoplite", Icons.Default.OpenInBrowser, Color(0xFF00897B), HOPLITE_ROUTE)
    )
    val context = LocalContext.current
    // Grille non paresseuse : elle vit dans une colonne défilante.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        actions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { action ->
                    QuickActionCard(action, Modifier.weight(1f)) {
                        if (action.route == HOPLITE_ROUTE) openHoplite(context) else onNavigate(action.route)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun QuickActionCard(action: QuickAction, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Card(
        modifier = modifier
            .height(96.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = action.color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(action.icon, contentDescription = action.title, modifier = Modifier.size(30.dp), tint = action.color)
            Spacer(modifier = Modifier.height(6.dp))
            Text(action.title, style = MaterialTheme.typography.bodyMedium, color = action.color, maxLines = 1)
        }
    }
}

private const val HOPLITE_ROUTE = "external:hoplite"

/** Ouvre Hoplite dans le navigateur : la connexion Google/GitHub y fonctionne, contrairement à une WebView. */
private fun openHoplite(context: android.content.Context) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://app.hoplite.sh"))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

data class QuickAction(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val route: String = ""
)
