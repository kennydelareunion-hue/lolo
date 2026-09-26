package com.termux.devcenter.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.devcenter.data.model.CommandResult
import com.termux.devcenter.data.repository.TerminalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TerminalViewModel : ViewModel() {
    private val repository = TerminalRepository()

    private val _commandHistory = MutableStateFlow<List<CommandResult>>(emptyList())
    val commandHistory: StateFlow<List<CommandResult>> = _commandHistory.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    fun executeCommand(command: String) {
        viewModelScope.launch {
            _isExecuting.value = true
            
            val result = repository.executeCommand(command)
            
            result.fold(
                onSuccess = { cmdResult ->
                    _commandHistory.value = _commandHistory.value + cmdResult
                },
                onFailure = { error ->
                    val errorResult = CommandResult(
                        command = command,
                        output = "",
                        error = error.message ?: "Erreur inconnue",
                        exitCode = 1
                    )
                    _commandHistory.value = _commandHistory.value + errorResult
                }
            )
            
            _isExecuting.value = false
        }
    }

    fun clearHistory() {
        _commandHistory.value = emptyList()
    }
}
