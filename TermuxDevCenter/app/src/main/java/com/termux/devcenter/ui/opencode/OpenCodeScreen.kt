package com.termux.devcenter.ui.opencode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenCodeScreen(viewModel: OpenCodeViewModel = viewModel()) {
    val messages by viewModel.messages.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val models by viewModel.models.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val error by viewModel.error.collectAsState()
    val loadingModels by viewModel.loadingModels.collectAsState()
    var promptText by remember { mutableStateOf("") }
    var modelMenuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1, Int.MAX_VALUE / 2)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
            IconButton(onClick = { viewModel.refreshModels() }, enabled = !loadingModels) {
                Icon(Icons.Default.Refresh, contentDescription = "Rafraîchir les modèles")
            }
            IconButton(onClick = { viewModel.clearConversation() }, enabled = messages.isNotEmpty()) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Nouvelle conversation")
            }
        }

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

        // Messages
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

        Spacer(modifier = Modifier.height(16.dp))

        // Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Entrez votre prompt…") },
                enabled = !isProcessing,
                minLines = 2,
                maxLines = 4
            )
            
            if (isProcessing) {
                IconButton(onClick = { viewModel.stopProcessing() }) {
                    Icon(Icons.Default.Stop, contentDescription = "Arrêter")
                }
            } else {
                Button(
                    onClick = {
                        if (promptText.isNotBlank()) {
                            viewModel.sendPrompt(promptText)
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
fun MessageBubble(message: com.termux.devcenter.data.model.OpenCodeMessage) {
    val isUser = message.role == "user"
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = if (isUser) "Vous" else "Assistant",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = message.content.ifEmpty { "…" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
