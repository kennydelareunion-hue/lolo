package com.termux.devcenter.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun TerminalScreen(viewModel: TerminalViewModel = viewModel()) {
    val commandHistory by viewModel.commandHistory.collectAsState()
    val isExecuting by viewModel.isExecuting.collectAsState()
    var commandText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(commandHistory.size) {
        if (commandHistory.isNotEmpty()) {
            listState.animateScrollToItem(commandHistory.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Terminal",
                style = MaterialTheme.typography.headlineMedium
            )
            IconButton(onClick = { viewModel.clearHistory() }) {
                Icon(Icons.Default.Clear, contentDescription = "Effacer")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Terminal output
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            )
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                items(commandHistory) { result ->
                    TerminalEntry(result)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Command input
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = commandText,
                onValueChange = { commandText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Entrez une commande…") },
                enabled = !isExecuting,
                singleLine = true
            )
            Button(
                onClick = {
                    if (commandText.isNotBlank()) {
                        viewModel.executeCommand(commandText)
                        commandText = ""
                    }
                },
                enabled = !isExecuting && commandText.isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = "Exécuter")
            }
        }

        if (isExecuting) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun TerminalEntry(result: com.termux.devcenter.data.model.CommandResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Command
        Text(
            text = "$ ${result.command}",
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF4CAF50),
            style = MaterialTheme.typography.bodyMedium
        )

        // Output
        if (result.output.isNotBlank()) {
            Text(
                text = result.output,
                fontFamily = FontFamily.Monospace,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Error
        if (result.error.isNotBlank()) {
            Text(
                text = result.error,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFF44336),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
