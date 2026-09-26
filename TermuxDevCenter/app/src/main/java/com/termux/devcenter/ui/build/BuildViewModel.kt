package com.termux.devcenter.ui.build

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.devcenter.data.repository.TerminalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BuildViewModel : ViewModel() {
    private val terminalRepository = TerminalRepository()

    private val _buildStatus = MutableStateFlow("")
    val buildStatus: StateFlow<String> = _buildStatus.asStateFlow()

    private val _buildLogs = MutableStateFlow("")
    val buildLogs: StateFlow<String> = _buildLogs.asStateFlow()

    private val _isBuilding = MutableStateFlow(false)
    val isBuilding: StateFlow<Boolean> = _isBuilding.asStateFlow()

    fun startBuild(projectPath: String) {
        viewModelScope.launch {
            _isBuilding.value = true
            _buildStatus.value = "Démarrage de la compilation..."
            _buildLogs.value = "=== Compilation démarrée ===\n"

            // Build command
            val command = "cd $projectPath && ./gradlew assembleDebug"
            
            val result = terminalRepository.executeCommand(command, projectPath)
            
            result.fold(
                onSuccess = { cmdResult ->
                    _buildLogs.value += cmdResult.output
                    
                    if (cmdResult.exitCode == 0) {
                        _buildStatus.value = "✓ Compilation réussie !"
                        _buildLogs.value += "\n\n=== BUILD SUCCESSFUL ===\n"
                    } else {
                        _buildStatus.value = "✗ Compilation échouée"
                        _buildLogs.value += "\n\n=== BUILD FAILED ===\n"
                        _buildLogs.value += cmdResult.error
                    }
                },
                onFailure = { error ->
                    _buildStatus.value = "✗ Erreur: ${error.message}"
                    _buildLogs.value += "\n\nERREUR: ${error.message}\n"
                }
            )
            
            _isBuilding.value = false
        }
    }
}
