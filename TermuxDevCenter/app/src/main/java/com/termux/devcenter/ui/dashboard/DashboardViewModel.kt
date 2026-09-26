package com.termux.devcenter.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.termux.devcenter.data.api.BridgeApiClient
import com.termux.devcenter.data.api.OmniExecClient
import com.termux.devcenter.data.model.ServerStatus
import com.termux.devcenter.data.omniroute.OmniRouteClient
import com.termux.devcenter.data.omniroute.OmniRouteSettings
import com.termux.devcenter.data.termux.TermuxLauncher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val _serverStatus = MutableStateFlow(ServerStatus())
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    private val _startMessage = MutableStateFlow<String?>(null)
    val startMessage: StateFlow<String?> = _startMessage.asStateFlow()

    private val bridgeClient = BridgeApiClient()
    private val omniExecClient = OmniExecClient()
    private val omniSettings = OmniRouteSettings(application)

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
            val bridge = async { bridgeClient.checkHealth().getOrDefault(false) }
            val omniExec = async { omniExecClient.checkHealth().getOrDefault(false) }
            val omniRoute = async { OmniRouteClient(omniSettings.current()).listModels() }
            val bridgeConnected = bridge.await()
            val models = omniRoute.await()

            _serverStatus.value = ServerStatus(
                openCodeConnected = false,
                serverActive = bridgeConnected,
                bridgeConnected = bridgeConnected,
                omniExecConnected = omniExec.await(),
                omniRouteConnected = models.isSuccess,
                omniRouteModelCount = models.getOrNull()?.size ?: 0,
                omniRouteMessage = models.exceptionOrNull()?.message
            )
        }
    }

    fun refreshStatus() {
        checkServersStatus()
    }

    fun startOmniRoute() {
        val app = getApplication<Application>()
        if (!TermuxLauncher.isTermuxInstalled(app)) {
            _startMessage.value = "Termux n'est pas installé."
            return
        }
        viewModelScope.launch {
            val command = omniSettings.current().startCommand
            TermuxLauncher.startOmniRoute(app, command).fold(
                onSuccess = {
                    _startMessage.value = "Démarrage demandé à Termux… (quelques secondes)"
                    repeat(10) {
                        delay(2000)
                        checkServersStatus()
                        if (_serverStatus.value.omniRouteConnected) {
                            _startMessage.value = null
                            return@launch
                        }
                    }
                    _startMessage.value = "OmniRoute ne répond pas encore. Vérifiez « allow-external-apps = true » " +
                        "dans ~/.termux/termux.properties et le journal ~/.omniroute/omniroute.log."
                },
                onFailure = {
                    _startMessage.value = "Échec du lancement via Termux : ${it.message}. " +
                        "Activez « allow-external-apps = true » dans ~/.termux/termux.properties."
                }
            )
        }
    }

    fun onPermissionDenied() {
        _startMessage.value = "Permission Termux refusée : autorisez « Exécuter des commandes dans Termux » " +
            "dans les paramètres Android de l'application."
    }
}
