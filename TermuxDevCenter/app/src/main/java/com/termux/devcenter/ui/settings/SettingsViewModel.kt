package com.termux.devcenter.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.termux.devcenter.data.omniroute.OmniRouteClient
import com.termux.devcenter.data.omniroute.OmniRouteConfig
import com.termux.devcenter.data.omniroute.OmniRouteSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val omniSettings = OmniRouteSettings(application)

    private val _settings = MutableStateFlow(mapOf<String, String>())
    val settings: StateFlow<Map<String, String>> = _settings.asStateFlow()

    private val _omniConfig = MutableStateFlow<OmniRouteConfig?>(null)
    val omniConfig: StateFlow<OmniRouteConfig?> = _omniConfig.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    init {
        viewModelScope.launch { _omniConfig.value = omniSettings.current() }
    }

    fun updateSetting(key: String, value: String) {
        _settings.value = _settings.value + (key to value)
    }

    /** Enregistre puis vérifie la connexion en listant les modèles. */
    fun saveAndTestOmniRoute(baseUrl: String, apiKey: String, startCommand: String) {
        viewModelScope.launch {
            omniSettings.save(baseUrl, apiKey, startCommand)
            val config = omniSettings.current()
            _omniConfig.value = config
            _testResult.value = "Test en cours…"
            _testResult.value = OmniRouteClient(config).listModels().fold(
                onSuccess = { models ->
                    if (models.isEmpty()) "✓ Connecté, mais aucun modèle : ajoutez un fournisseur dans l'onglet OmniRoute."
                    else "✓ Connecté — ${models.size} modèle(s) : ${models.take(5).joinToString()}" +
                        if (models.size > 5) "…" else ""
                },
                onFailure = { "✗ ${it.message}" }
            )
        }
    }
}
