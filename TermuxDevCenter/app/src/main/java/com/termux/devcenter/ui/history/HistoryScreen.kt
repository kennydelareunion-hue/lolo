package com.termux.devcenter.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.termux.devcenter.data.history.ChatHistoryStore
import com.termux.devcenter.data.history.ChatNavigation
import com.termux.devcenter.data.history.ChatSummary
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Liste des conversations du Chat IA ; un appui rouvre la conversation avec son modèle et sa mémoire. */
@Composable
fun HistoryScreen(onOpenChat: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ChatHistoryStore.get(context) }
    val chats by store.summaries.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<ChatSummary?>(null) }
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    val visible = remember(chats, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) chats else chats.filter {
            it.title.lowercase().contains(q) || it.modelName.orEmpty().lowercase().contains(q) ||
                it.provider.orEmpty().lowercase().contains(q)
        }
    }

    toDelete?.let { chat ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Supprimer la conversation ?") },
            text = { Text("« ${chat.title} » sera supprimée définitivement.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { store.delete(chat.id) }
                    toDelete = null
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } }
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Historique", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = {
                ChatNavigation.openRequest.value = ChatNavigation.NEW_CHAT
                onOpenChat()
            }) {
                Icon(Icons.Default.AddComment, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Nouveau")
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Rechercher une conversation") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        if (visible.isEmpty()) {
            Text(
                if (chats.isEmpty()) "Aucune conversation pour l'instant. Elles apparaissent ici dès votre premier message dans Chat IA."
                else "Aucun résultat.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp)
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.id }) { chat ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        ChatNavigation.openRequest.value = chat.id
                        onOpenChat()
                    }
                ) {
                    Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(chat.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(chat.modelName, chat.provider?.let { "via $it" }).joinToString(" "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${formatter.format(Date(chat.updatedAt))} · ${chat.messageCount} message(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { toDelete = chat }) {
                            Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                        }
                    }
                }
            }
        }
    }
}
