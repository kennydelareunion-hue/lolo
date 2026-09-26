package com.termux.devcenter.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.termux.devcenter.data.hoplite.HopliteClient
import com.termux.devcenter.data.hoplite.HopliteProject
import com.termux.devcenter.data.mcp.McpClient
import com.termux.devcenter.data.omniroute.FreeModelCatalog
import com.termux.devcenter.data.omniroute.ModelSelection
import com.termux.devcenter.data.omniroute.ModelTier
import com.termux.devcenter.data.omniroute.OmniRouteClient
import com.termux.devcenter.data.omniroute.OmniRouteConfig
import com.termux.devcenter.data.omniroute.OmniRouteSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val omniSettings = OmniRouteSettings(application)

    private val _omniConfig = MutableStateFlow<OmniRouteConfig?>(null)
    val omniConfig: StateFlow<OmniRouteConfig?> = _omniConfig.asStateFlow()

    private val _hopliteProjects = MutableStateFlow<List<HopliteProject>>(emptyList())
    val hopliteProjects: StateFlow<List<HopliteProject>> = _hopliteProjects.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    init {
        viewModelScope.launch {
            val config = omniSettings.current()
            _omniConfig.value = config
            if (config.hopliteApiKey.isNotBlank()) {
                HopliteClient(config.hopliteApiKey).listProjects().onSuccess { _hopliteProjects.value = it }
            }
        }
    }

    /** Enregistre puis vérifie OmniRoute (liste des modèles) et Omni-Exec (liste des outils). */
    fun saveAndTest(edited: OmniRouteConfig) {
        viewModelScope.launch {
            var config = edited.copy(model = omniSettings.current().model)
            omniSettings.save(config)
            _omniConfig.value = config
            _testResult.value = "Test en cours…"
            val models = async {
                OmniRouteClient(config).listModelInfo(FreeModelCatalog.load(getApplication()), config.connectedOnly)
            }
            val tools = async { if (config.mcpEnabled) McpClient.forUrl(config.mcpUrl).listTools() else null }
            val hopliteModels = async {
                if (config.hopliteApiKey.isBlank()) null else HopliteClient(config.hopliteApiKey).listModels()
            }
            val hopliteProjects = async {
                if (config.hopliteApiKey.isBlank()) null else HopliteClient(config.hopliteApiKey).listProjects()
            }

            val omniLine = models.await().fold(
                onSuccess = { list ->
                    if (list.isEmpty()) "✓ OmniRoute connecté, mais aucun modèle : ajoutez un fournisseur."
                    else "✓ OmniRoute : ${list.size} modèle(s) connecté(s) — " +
                        "${list.count { it.tier == ModelTier.PRO }} Pro, ${list.count { it.tier == ModelTier.FREE }} gratuit(s)" +
                        (list.firstOrNull { it.id == ModelSelection.PREFERRED_MODEL }?.let { "\n✓ Claude Sonnet 4.5 via Kiro disponible" } ?: "")
                },
                onFailure = { "✗ ${it.message}" }
            )
            val execLine = tools.await()?.fold(
                onSuccess = { "✓ Omni-Exec : ${it.size} outil(s) — ${it.joinToString { t -> t.name }}" },
                onFailure = { "✗ ${it.message}" }
            ) ?: "Omni-Exec désactivé"
            val projects = hopliteProjects.await()?.getOrNull().orEmpty()
            _hopliteProjects.value = projects
            if (projects.isNotEmpty() && projects.none { it.id == config.hopliteProjectId }) {
                config = config.copy(hopliteProjectId = projects.first().id)
                omniSettings.save(config)
                _omniConfig.value = config
            }
            val hopliteLine = hopliteModels.await()?.fold(
                onSuccess = { models ->
                    val project = projects.firstOrNull { it.id == config.hopliteProjectId }?.name ?: "aucun projet"
                    "✓ Hoplite : ${models.size} modèle(s) — projet « $project »"
                },
                onFailure = { "✗ ${it.message}" }
            ) ?: "Hoplite : pas de clé API"
            _testResult.value = "$omniLine\n\n$execLine\n\n$hopliteLine"
        }
    }
}
