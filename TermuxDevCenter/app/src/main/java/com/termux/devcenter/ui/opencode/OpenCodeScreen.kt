package com.termux.devcenter.ui.opencode

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.termux.devcenter.data.model.OpenCodeMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenCodeScreen(viewModel: OpenCodeViewModel = viewModel()) {
    val messages by viewModel.messages.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val models by viewModel.models.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val error by viewModel.error.collectAsState()
    val loadingModels by viewModel.loadingModels.collectAsState()
    val mcpTools by viewModel.mcpTools.collectAsState()
    val mcpStatus by viewModel.mcpStatus.collectAsState()
    val pendingTool by viewModel.pendingTool.collectAsState()
    var promptText by remember { mutableStateOf("") }
    var modelMenuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1, Int.MAX_VALUE / 2)
        }
    }

    pendingTool?.let { pending ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Autoriser la commande ?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Claude veut utiliser l'outil Omni-Exec « ${pending.toolName} » :")
                    Spacer(Modifier.height(8.dp))
                    Text(pending.arguments, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    Button(onClick = { viewModel.answerPendingTool(ToolDecision.RUN) }) { Text("Exécuter") }
                    TextButton(onClick = { viewModel.answerPendingTool(ToolDecision.RUN_ALWAYS) }) {
                        Text("Toujours autoriser (cette conversation)")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.answerPendingTool(ToolDecision.DENY) }) { Text("Refuser") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExposedDropdownMenuBox(
                expanded = modelMenuOpen,
                onExpandedChange = { modelMenuOpen = it && models.isNotEmpty() },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = selectedModel.ifBlank { if (loadingModels) "Chargement…" else "Aucun modèle" },
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text("Modèle OmniRoute") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuOpen) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = modelMenuOpen,
                    onDismissRequest = { modelMenuOpen = false }
                ) {
                    models.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model) },
                            onClick = {
                                viewModel.selectModel(model)
                                modelMenuOpen = false
                            }
                        )
                    }
                }
            }
            IconButton(onClick = { viewModel.refreshModels(); viewModel.refreshTools() }, enabled = !loadingModels) {
                Icon(Icons.Default.Refresh, contentDescription = "Rafraîchir")
            }
            IconButton(onClick = { viewModel.clearConversation() }, enabled = messages.isNotEmpty()) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Nouvelle conversation")
            }
        }

        AssistChip(
            onClick = { viewModel.refreshTools() },
            label = { Text(mcpStatus, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp)) },
            colors = AssistChipDefaults.assistChipColors(
                labelColor = if (mcpTools.isNotEmpty()) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.fillMaxWidth()
        )

        error?.let {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = it,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Demandez à Claude…") },
                enabled = !isProcessing,
                minLines = 1,
                maxLines = 5
            )
            if (isProcessing) {
                IconButton(onClick = { viewModel.stopProcessing() }) {
                    Icon(Icons.Default.Stop, contentDescription = "Arrêter")
                }
            } else {
                Button(
                    onClick = {
                        if (promptText.isNotBlank()) {
                            viewModel.sendPrompt(promptText.trim())
                            promptText = ""
                        }
                    },
                    enabled = promptText.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Envoyer")
                }
            }
        }

        if (isProcessing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun MessageBubble(message: OpenCodeMessage) {
    val isUser = message.role == "user"
    val isTool = message.role == "tool"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = if (isTool) 340.dp else 300.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isUser -> MaterialTheme.colorScheme.primaryContainer
                    isTool -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = when {
                        isUser -> "Vous"
                        isTool -> "Omni-Exec"
                        else -> "Assistant"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = message.content.ifEmpty { "…" },
                        style = if (isTool) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        fontFamily = if (isTool) FontFamily.Monospace else null
                    )
                }
            }
        }
    }
}
