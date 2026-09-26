package com.termux.devcenter.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.termux.devcenter.data.mcp.McpClient
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

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    init {
        viewModelScope.launch { _omniConfig.value = omniSettings.current() }
    }

    /** Enregistre puis vérifie OmniRoute (liste des modèles) et Omni-Exec (liste des outils). */
    fun saveAndTest(edited: OmniRouteConfig) {
        viewModelScope.launch {
            val config = edited.copy(model = omniSettings.current().model)
            omniSettings.save(config)
            _omniConfig.value = config
            _testResult.value = "Test en cours…"
            val models = async { OmniRouteClient(config).listModels() }
            val tools = async { if (config.mcpEnabled) McpClient.forUrl(config.mcpUrl).listTools() else null }

            val omniLine = models.await().fold(
                onSuccess = { list ->
                    if (list.isEmpty()) "✓ OmniRoute connecté, mais aucun modèle : ajoutez un fournisseur."
                    else "✓ OmniRoute : ${list.size} modèle(s) — ${list.take(4).joinToString()}" +
                        if (list.size > 4) "…" else ""
                },
                onFailure = { "✗ ${it.message}" }
            )
            val execLine = tools.await()?.fold(
                onSuccess = { "✓ Omni-Exec : ${it.size} outil(s) — ${it.joinToString { t -> t.name }}" },
                onFailure = { "✗ ${it.message}" }
            ) ?: "Omni-Exec désactivé"
            _testResult.value = "$omniLine\n\n$execLine"
        }
    }
}
