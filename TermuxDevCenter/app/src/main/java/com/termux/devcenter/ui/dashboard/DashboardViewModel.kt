package com.termux.devcenter.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.termux.devcenter.data.api.BridgeApiClient
import com.termux.devcenter.data.mcp.McpClient
import com.termux.devcenter.data.model.ServerStatus
import com.termux.devcenter.data.omniroute.OmniRouteClient
import com.termux.devcenter.data.omniroute.OmniRouteException
import com.termux.devcenter.data.omniroute.OmniRouteSettings
import com.termux.devcenter.data.termux.TermuxLauncher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Service { OMNIROUTE, OMNI_EXEC }

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val _serverStatus = MutableStateFlow(ServerStatus())
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    private val _startMessage = MutableStateFlow<String?>(null)
    val startMessage: StateFlow<String?> = _startMessage.asStateFlow()

    private val bridgeClient = BridgeApiClient()
    private val omniSettings = OmniRouteSettings(application)

    init {
        viewModelScope.launch {
            while (true) {
                checkServersStatus()
                delay(5000)
            }
        }
    }

    private suspend fun checkServersStatus() {
        val config = omniSettings.current()
        val (bridgeOk, models, tools) = kotlinx.coroutines.coroutineScope {
            val bridge = async { bridgeClient.checkHealth().getOrDefault(false) }
            val omniRoute = async { OmniRouteClient(config).listModels() }
            val omniExec = async {
                if (config.mcpEnabled) McpClient.forUrl(config.mcpUrl).listTools() else null
            }
            Triple(bridge.await(), omniRoute.await(), omniExec.await())
        }
        val modelError = models.exceptionOrNull()
        _serverStatus.value = ServerStatus(
            serverActive = bridgeOk,
            bridgeConnected = bridgeOk,
            omniExecConnected = tools?.isSuccess == true,
            omniExecToolCount = tools?.getOrNull()?.size ?: 0,
            omniExecMessage = if (tools == null) "Désactivé dans Réglages" else tools.exceptionOrNull()?.message,
            omniRouteConnected = models.isSuccess,
            omniRouteModelCount = models.getOrNull()?.size ?: 0,
            omniRouteMessage = modelError?.message,
            omniRouteAuthRequired = (modelError as? OmniRouteException)?.isAuthError == true,
            checked = true
        )
    }

    fun refreshStatus() {
        viewModelScope.launch { checkServersStatus() }
    }

    private fun ServerStatus.isUp(service: Service) = when (service) {
        Service.OMNIROUTE -> omniRouteConnected || omniRouteAuthRequired
        Service.OMNI_EXEC -> omniExecConnected
    }

    fun start(service: Service) {
        val app = getApplication<Application>()
        if (!TermuxLauncher.isTermuxInstalled(app)) {
            _startMessage.value = "Termux n'est pas installé."
            return
        }
        viewModelScope.launch {
            val config = omniSettings.current()
            val (label, result) = when (service) {
                Service.OMNIROUTE -> "OmniRoute" to
                    TermuxLauncher.startDetached(app, config.startCommand, TermuxLauncher.OMNIROUTE_LOG)
                Service.OMNI_EXEC -> "Omni-Exec" to
                    TermuxLauncher.startDetached(app, config.mcpStartCommand, TermuxLauncher.OMNI_EXEC_LOG)
            }
            val log = if (service == Service.OMNIROUTE) TermuxLauncher.OMNIROUTE_LOG else TermuxLauncher.OMNI_EXEC_LOG
            result.fold(
                onSuccess = {
                    _startMessage.value = "Démarrage de $label demandé à Termux…"
                    repeat(10) {
                        delay(2000)
                        checkServersStatus()
                        if (_serverStatus.value.isUp(service)) {
                            _startMessage.value = "$label est démarré."
                            return@launch
                        }
                    }
                    _startMessage.value = "$label ne répond pas encore. Vérifiez « allow-external-apps = true » " +
                        "dans ~/.termux/termux.properties, la commande dans Réglages et le journal $log."
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
            "dans les paramètres Android de l'application (Autorisations > Autorisations supplémentaires)."
    }
}
