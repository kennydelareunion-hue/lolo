package com.termux.devcenter.ui.opencode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.devcenter.data.model.OpenCodeMessage
import com.termux.devcenter.data.repository.TerminalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OpenCodeViewModel : ViewModel() {
    private val terminalRepository = TerminalRepository()

    private val _messages = MutableStateFlow<List<OpenCodeMessage>>(emptyList())
    val messages: StateFlow<List<OpenCodeMessage>> = _messages.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    fun sendPrompt(prompt: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            
            // Add user message
            _messages.value = _messages.value + OpenCodeMessage(
                role = "user",
                content = prompt
            )

            // Execute via terminal (simplified for now)
            val result = terminalRepository.executeCommand("echo 'OpenCode: $prompt'")
            
            result.fold(
                onSuccess = { cmdResult ->
                    _messages.value = _messages.value + OpenCodeMessage(
                        role = "assistant",
                        content = cmdResult.output.ifBlank { "Commande exécutée avec succès" }
                    )
                },
                onFailure = { error ->
                    _messages.value = _messages.value + OpenCodeMessage(
                        role = "assistant",
                        content = "Erreur: ${error.message}"
                    )
                }
            )
            
            _isProcessing.value = false
        }
    }

    fun stopProcessing() {
        _isProcessing.value = false
    }
}
