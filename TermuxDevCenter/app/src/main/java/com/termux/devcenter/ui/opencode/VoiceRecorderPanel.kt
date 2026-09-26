package com.termux.devcenter.ui.opencode

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.termux.devcenter.data.voice.VoiceDictation
import kotlinx.coroutines.delay

/** Panneau d'enregistrement façon ChatGPT : texte en direct, niveau sonore, minuteur. */
@Composable
fun VoiceRecorderPanel(
    state: VoiceDictation.State,
    onCancel: () -> Unit,
    onInsert: () -> Unit,
    onSend: () -> Unit
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val elapsed = ((now - state.startedAt).coerceAtLeast(0) / 1000).toInt()

    // Historique court du niveau sonore pour dessiner une onde qui défile.
    val levels = remember { mutableStateListOf<Float>().apply { repeat(BAR_COUNT) { add(0f) } } }
    LaunchedEffect(state.level) {
        levels.removeAt(0)
        levels.add(state.level)
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .alpha(pulse)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "À l'écoute… %d:%02d".format(elapsed / 60, elapsed % 60),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "Les pauses n'arrêtent pas l'enregistrement",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().height(36.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                levels.forEach { level ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(0.12f + 0.88f * level)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState())) {
                if (state.committed.isEmpty() && state.partial.isEmpty()) {
                    Text("Parlez…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(state.committed, style = MaterialTheme.typography.bodyMedium)
                    if (state.partial.isNotEmpty()) {
                        Text(
                            state.partial,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Annuler") }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onInsert) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Terminer")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSend, enabled = state.text.isNotBlank()) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Envoyer")
                }
            }
        }
    }
}

private const val BAR_COUNT = 32
