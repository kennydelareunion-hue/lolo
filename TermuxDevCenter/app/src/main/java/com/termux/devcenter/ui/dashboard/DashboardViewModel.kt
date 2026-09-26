package com.termux.devcenter.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.devcenter.data.api.BridgeApiClient
import com.termux.devcenter.data.api.OmniExecClient
import com.termux.devcenter.data.model.ServerStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel : ViewModel() {
    private val _serverStatus = MutableStateFlow(ServerStatus())
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    private val bridgeClient = BridgeApiClient()
    private val omniExecClient = OmniExecClient()

    init {
        checkServersStatus()
        startStatusPolling()
    }

    private fun startStatusPolling() {
        viewModelScope.launch {
            while (true) {
                checkServersStatus()
                delay(5000) // Check every 5 seconds
            }
        }
    }

    private fun checkServersStatus() {
        viewModelScope.launch {
            val bridgeConnected = bridgeClient.checkHealth().getOrDefault(false)
            val omniExecConnected = omniExecClient.checkHealth().getOrDefault(false)

            _serverStatus.value = ServerStatus(
                openCodeConnected = false, // TODO: Check OpenCode status
                serverActive = bridgeConnected,
                bridgeConnected = bridgeConnected,
                omniExecConnected = omniExecConnected
            )
        }
    }

    fun refreshStatus() {
        checkServersStatus()
    }
}
