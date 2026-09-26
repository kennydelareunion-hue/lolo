package com.termux.devcenter.ui.opencode

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.termux.devcenter.data.model.OpenCodeMessage
import com.termux.devcenter.data.omniroute.ChatMessage
import com.termux.devcenter.data.omniroute.OmniRouteClient
import com.termux.devcenter.data.omniroute.OmniRouteSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Chat avec les modèles exposés par OmniRoute (ex. Claude Sonnet 4.5 via Kiro). */
class OpenCodeViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = OmniRouteSettings(application)

    private val _messages = MutableStateFlow<List<OpenCodeMessage>>(emptyList())
    val messages: StateFlow<List<OpenCodeMessage>> = _messages.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _models = MutableStateFlow<List<String>>(emptyList())
    val models: StateFlow<List<String>> = _models.asStateFlow()

    private val _selectedModel = MutableStateFlow("")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loadingModels = MutableStateFlow(false)
    val loadingModels: StateFlow<Boolean> = _loadingModels.asStateFlow()

    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            // Recharge les modèles quand l'URL ou la clé changent dans les paramètres.
            settings.config.map { it.normalizedBaseUrl to it.apiKey }.distinctUntilChanged()
                .collect { refreshModels() }
        }
    }

    fun refreshModels() {
        viewModelScope.launch {
            _loadingModels.value = true
            val config = settings.current()
            OmniRouteClient(config).listModels().fold(
                onSuccess = { list ->
                    _models.value = list
                    _error.value = if (list.isEmpty()) {
                        "OmniRoute ne propose aucun modèle : connectez un fournisseur dans l'onglet OmniRoute."
                    } else null
                    val chosen = config.model.takeIf { it in list } ?: pickDefault(list)
                    _selectedModel.value = chosen
                    if (chosen != config.model) settings.setModel(chosen)
                },
                onFailure = { _error.value = it.message }
            )
            _loadingModels.value = false
        }
    }

    private fun pickDefault(list: List<String>): String =
        list.firstOrNull { it.contains("claude", true) && it.contains("sonnet", true) }
            ?: list.firstOrNull { it.contains("claude", true) }
            ?: list.firstOrNull().orEmpty()

    fun selectModel(model: String) {
        _selectedModel.value = model
        viewModelScope.launch { settings.setModel(model) }
    }

    fun sendPrompt(prompt: String) {
        val model = _selectedModel.value
        if (model.isBlank()) {
            _error.value = "Aucun modèle sélectionné. Vérifiez la connexion à OmniRoute."
            refreshModels()
            return
        }
        _error.value = null
        val history = _messages.value + OpenCodeMessage(role = "user", content = prompt)
        _messages.value = history + OpenCodeMessage(role = "assistant", content = "")
        _isProcessing.value = true

        streamJob = viewModelScope.launch {
            val client = OmniRouteClient(settings.current())
            val builder = StringBuilder()
            try {
                client.streamChat(model, history.map { ChatMessage(it.role, it.content) })
                    .collect { chunk ->
                        builder.append(chunk)
                        updateLastAssistant(builder.toString())
                    }
                if (builder.isEmpty()) updateLastAssistant("(réponse vide)")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message
                updateLastAssistant(builder.toString().ifEmpty { "Erreur : ${e.message}" })
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun updateLastAssistant(content: String) {
        val current = _messages.value
        if (current.isEmpty()) return
        _messages.value = current.dropLast(1) + current.last().copy(content = content)
    }

    fun stopProcessing() {
        streamJob?.cancel()
        _isProcessing.value = false
        val last = _messages.value.lastOrNull()
        if (last?.role == "assistant" && last.content.isEmpty()) updateLastAssistant("(arrêté)")
    }

    fun clearConversation() {
        stopProcessing()
        _messages.value = emptyList()
        _error.value = null
    }
}
