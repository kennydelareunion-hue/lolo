package com.termux.devcenter.ui.opencode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.termux.devcenter.data.omniroute.ModelInfo
import com.termux.devcenter.data.omniroute.ModelSelection
import com.termux.devcenter.data.omniroute.ModelTier

val FreeColor = Color(0xFF2E7D32)
val ProColor = Color(0xFF7B1FA2)
val ComboColor = Color(0xFF546E7A)
val HopliteColor = Color(0xFF00897B)

@Composable
fun TierBadge(tier: ModelTier) {
    val (text, color) = when (tier) {
        ModelTier.FREE -> "GRATUIT" to FreeColor
        ModelTier.PRO -> "PRO" to ProColor
        ModelTier.COMBO -> "COMBO" to ComboColor
        ModelTier.HOPLITE -> "HOPLITE" to HopliteColor
    }
    Surface(color = color, shape = MaterialTheme.shapes.small) {
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

private enum class PickerTab(val title: String) {
    FAVORITES("★ Favoris"), PRO("Pro"), FREE("Gratuit"), HOPLITE("Hoplite"), ALL("Tous")
}

@Composable
fun ModelPickerDialog(
    models: List<ModelInfo>,
    selected: String,
    favorites: Set<String>,
    connectedOnly: Boolean,
    onConnectedOnlyChange: (Boolean) -> Unit,
    providerCounts: List<Pair<String, Int>>,
    enabledProviders: Set<String>,
    onToggleProvider: (String) -> Unit,
    onSetAllProviders: (Boolean) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val favoriteModels = remember(models, favorites) { models.filter { it.id in favorites } }
    var tab by remember { mutableStateOf(if (favoriteModels.isNotEmpty()) PickerTab.FAVORITES else PickerTab.PRO) }
    var query by remember { mutableStateOf("") }
    var providersOpen by remember { mutableStateOf(false) }

    val source = when (tab) {
        PickerTab.FAVORITES -> favoriteModels
        PickerTab.PRO -> models.filter { it.tier == ModelTier.PRO }
        PickerTab.FREE -> models.filter { it.tier == ModelTier.FREE }
        PickerTab.HOPLITE -> models.filter { it.isHoplite }
        PickerTab.ALL -> models
    }
    val groups = remember(source, query) { ModelSelection.groupByProvider(ModelSelection.search(source, query)) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.large
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Choisir un modèle", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Rechercher (ex. claude kiro)") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 0.dp) {
                    PickerTab.entries.forEach { t ->
                        val count = when (t) {
                            PickerTab.FAVORITES -> favoriteModels.size
                            PickerTab.PRO -> models.count { it.tier == ModelTier.PRO }
                            PickerTab.FREE -> models.count { it.tier == ModelTier.FREE }
                            PickerTab.HOPLITE -> models.count { it.isHoplite }
                            PickerTab.ALL -> models.size
                        }
                        Tab(selected = tab == t, onClick = { tab = t }, text = { Text("${t.title} ($count)") })
                    }
                }
                OutlinedButton(onClick = { providersOpen = !providersOpen }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Fournisseurs affichés : ${enabledProviders.count { p -> providerCounts.any { it.first == p } }}" +
                            "/${providerCounts.size} ${if (providersOpen) "▲" else "▼"}"
                    )
                }
                if (providersOpen) {
                    ProviderFilter(providerCounts, enabledProviders, onToggleProvider, onSetAllProviders)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Seulement mes comptes connectés",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = connectedOnly, onCheckedChange = onConnectedOnlyChange)
                }
                if (tab == PickerTab.HOPLITE) {
                    Text(
                        if (models.none { it.isHoplite }) "Ajoutez votre clé API Hoplite dans Réglages pour voir ces modèles."
                        else "Chaque conversation Hoplite crée un thread (agent + sandbox) qui consomme vos crédits Hoplite.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (tab == PickerTab.FREE) {
                    Text(
                        "Gratuit = offre gratuite (quota limité) selon le catalogue OmniRoute.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (groups.isEmpty()) {
                        item {
                            Text(
                                when (tab) {
                                    PickerTab.FAVORITES -> "Aucun favori : touchez ☆ à côté d'un modèle."
                                    PickerTab.HOPLITE -> "Aucun modèle Hoplite."
                                    else -> "Aucun modèle : activez des fournisseurs ci-dessus."
                                },
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    groups.forEach { (provider, list) ->
                        item(key = "h-$provider-${tab.name}") {
                            Text(
                                "$provider (${list.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(list, key = { "${tab.name}-${it.id}" }) { model ->
                            ModelRow(
                                model = model,
                                isSelected = model.id == selected,
                                isFavorite = model.id in favorites,
                                onToggleFavorite = { onToggleFavorite(model.id) },
                                onClick = { onSelect(model.id); onDismiss() }
                            )
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Fermer") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderFilter(
    providerCounts: List<Pair<String, Int>>,
    enabled: Set<String>,
    onToggle: (String) -> Unit,
    onSetAll: (Boolean) -> Unit
) {
    Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
        Text(
            "Cochez uniquement les fournisseurs que vous avez connectés (ex. kiro).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row {
            TextButton(onClick = { onSetAll(true) }) { Text("Tout") }
            TextButton(onClick = { onSetAll(false) }) { Text("Aucun") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            providerCounts.forEach { (provider, count) ->
                FilterChip(
                    selected = provider in enabled,
                    onClick = { onToggle(provider) },
                    label = { Text("$provider ($count)") }
                )
            }
        }
    }
}

@Composable
private fun ModelRow(
    model: ModelInfo,
    isSelected: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(28.dp)) {
            if (isSelected) Icon(Icons.Default.Check, contentDescription = "Sélectionné", tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(model.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                model.id,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TierBadge(model.tier)
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
                tint = if (isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
