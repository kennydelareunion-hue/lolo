package com.termux.devcenter.ui.opencode

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.termux.devcenter.data.mcp.McpClient
import com.termux.devcenter.data.mcp.McpTool
import com.termux.devcenter.data.model.OpenCodeMessage
import com.termux.devcenter.data.omniroute.ChatEvent
import com.termux.devcenter.data.omniroute.OmniRouteClient
import com.termux.devcenter.data.omniroute.OmniRouteConfig
import com.termux.devcenter.data.omniroute.OmniRouteSettings
import com.termux.devcenter.data.omniroute.ToolCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class ToolDecision { RUN, RUN_ALWAYS, DENY }

data class PendingToolCall(
    val toolName: String,
    val arguments: String,
    val decision: CompletableDeferred<ToolDecision>
)

/**
 * Chat avec les modèles exposés par OmniRoute (ex. Claude Sonnet 4.5 via Kiro).
 * Les outils du serveur MCP Omni-Exec sont proposés au modèle, qui peut ainsi
 * exécuter des commandes dans Termux (après confirmation de l'utilisateur).
 */
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

    private val _mcpTools = MutableStateFlow<List<McpTool>>(emptyList())
    val mcpTools: StateFlow<List<McpTool>> = _mcpTools.asStateFlow()

    private val _mcpStatus = MutableStateFlow("Omni-Exec : vérification…")
    val mcpStatus: StateFlow<String> = _mcpStatus.asStateFlow()

    private val _pendingTool = MutableStateFlow<PendingToolCall?>(null)
    val pendingTool: StateFlow<PendingToolCall?> = _pendingTool.asStateFlow()

    /** Historique au format OpenAI, y compris appels et résultats d'outils. */
    private var conversation = JsonArray()
    private var autoApprove = false
    private var streamJob: Job? = null
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()

    init {
        viewModelScope.launch {
            settings.config.map { it.normalizedBaseUrl to it.apiKey }.distinctUntilChanged()
                .collect { refreshModels() }
        }
        viewModelScope.launch {
            settings.config.map { it.mcpUrl to it.mcpEnabled }.distinctUntilChanged()
                .collect { refreshTools() }
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

    fun refreshTools() {
        viewModelScope.launch {
            val config = settings.current()
            if (!config.mcpEnabled) {
                _mcpTools.value = emptyList()
                _mcpStatus.value = "Omni-Exec désactivé (Réglages)"
                return@launch
            }
            McpClient.forUrl(config.mcpUrl).listTools().fold(
                onSuccess = {
                    _mcpTools.value = it
                    _mcpStatus.value = "Omni-Exec : ${it.size} outil(s) — ${it.joinToString { t -> t.name }}"
                },
                onFailure = {
                    _mcpTools.value = emptyList()
                    _mcpStatus.value = "Omni-Exec indisponible : ${it.message}"
                }
            )
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
        conversation.add(message("user", prompt))
        addUi("user", prompt)
        _isProcessing.value = true

        streamJob = viewModelScope.launch {
            try {
                runAgentLoop(model)
            } catch (e: CancellationException) {
                closeDanglingTurn("(interrompu)")
                throw e
            } catch (e: Exception) {
                _error.value = e.message
                closeDanglingTurn("Erreur : ${e.message}")
            } finally {
                _isProcessing.value = false
                _pendingTool.value?.decision?.cancel()
                _pendingTool.value = null
            }
        }
    }

    private suspend fun runAgentLoop(model: String) {
        val config = settings.current()
        val client = OmniRouteClient(config)
        val tools = if (config.mcpEnabled) _mcpTools.value else emptyList()
        val toolsJson = toOpenAiTools(tools)

        repeat(MAX_TOOL_ROUNDS) {
            addUi("assistant", "")
            val text = StringBuilder()
            var calls = emptyList<ToolCall>()
            client.streamCompletion(model, requestMessages(tools.isNotEmpty()), toolsJson).collect { event ->
                when (event) {
                    is ChatEvent.Text -> {
                        text.append(event.text)
                        updateLastUi(text.toString())
                    }
                    is ChatEvent.ToolCalls -> calls = event.calls
                }
            }

            val assistant = JsonObject().apply {
                addProperty("role", "assistant")
                if (text.isEmpty()) add("content", JsonNull.INSTANCE) else addProperty("content", text.toString())
            }
            if (calls.isEmpty()) {
                if (text.isEmpty()) updateLastUi("(réponse vide)")
                conversation.add(assistant)
                return
            }
            if (text.isEmpty()) removeLastUi()

            assistant.add("tool_calls", JsonArray().apply {
                calls.forEach { call ->
                    add(JsonObject().apply {
                        addProperty("id", call.id)
                        addProperty("type", "function")
                        add("function", JsonObject().apply {
                            addProperty("name", call.name)
                            addProperty("arguments", call.arguments.ifBlank { "{}" })
                        })
                    })
                }
            })
            // Tour validé seulement quand tous les résultats sont connus, pour ne jamais
            // envoyer au modèle un appel d'outil sans réponse.
            val round = mutableListOf<JsonObject>(assistant)
            for (call in calls) {
                val output = executeTool(config, call)
                round += JsonObject().apply {
                    addProperty("role", "tool")
                    addProperty("tool_call_id", call.id)
                    addProperty("content", output)
                }
            }
            round.forEach { conversation.add(it) }
        }
        addUi("assistant", "Limite de $MAX_TOOL_ROUNDS étapes d'outils atteinte pour cette demande.")
        conversation.add(message("assistant", "Limite d'étapes d'outils atteinte."))
    }

    private suspend fun executeTool(config: OmniRouteConfig, call: ToolCall): String {
        val args = runCatching { JsonParser.parseString(call.arguments.ifBlank { "{}" }).asJsonObject }
            .getOrElse { JsonObject() }
        val pretty = prettyGson.toJson(args)
        val header = "🔧 ${call.name}\n$pretty"
        addUi("tool", header)

        if (config.confirmCommands && !autoApprove) {
            val decision = CompletableDeferred<ToolDecision>()
            _pendingTool.value = PendingToolCall(call.name, pretty, decision)
            val choice = try {
                decision.await()
            } finally {
                _pendingTool.value = null
            }
            when (choice) {
                ToolDecision.DENY -> {
                    updateLastUi("$header\n✗ Refusé")
                    return "L'utilisateur a refusé l'exécution de cet outil."
                }
                ToolDecision.RUN_ALWAYS -> autoApprove = true
                ToolDecision.RUN -> Unit
            }
        }

        updateLastUi("$header\n⏳ Exécution…")
        val output = McpClient.forUrl(config.mcpUrl).callTool(call.name, args).fold(
            onSuccess = { (if (it.isError) "ERREUR : " else "") + it.text.ifBlank { "(aucune sortie)" } },
            onFailure = { "Erreur Omni-Exec : ${it.message}" }
        )
        updateLastUi("$header\n→ ${output.take(UI_OUTPUT_LIMIT)}${if (output.length > UI_OUTPUT_LIMIT) "\n…" else ""}")
        return output.take(MODEL_OUTPUT_LIMIT)
    }

    fun answerPendingTool(decision: ToolDecision) {
        _pendingTool.value?.decision?.complete(decision)
    }

    private fun requestMessages(withTools: Boolean): JsonArray = JsonArray().apply {
        if (withTools) add(message("system", SYSTEM_PROMPT))
        addAll(conversation.deepCopy())
    }

    private fun toOpenAiTools(tools: List<McpTool>): JsonArray? {
        if (tools.isEmpty()) return null
        return JsonArray().apply {
            tools.forEach { tool ->
                add(JsonObject().apply {
                    addProperty("type", "function")
                    add("function", JsonObject().apply {
                        addProperty("name", tool.name)
                        addProperty("description", tool.description.ifBlank { tool.name })
                        add("parameters", tool.inputSchema)
                    })
                })
            }
        }
    }

    /** Garde l'historique valide (alternance user/assistant) après une erreur ou un arrêt. */
    private fun closeDanglingTurn(placeholder: String) {
        val last = _messages.value.lastOrNull()
        if (last?.role == "assistant" && last.content.isEmpty()) updateLastUi(placeholder)
        val lastRole = conversation.lastOrNull()?.asJsonObject?.get("role")?.asString
        if (lastRole == "user" || lastRole == "tool") conversation.add(message("assistant", placeholder))
    }

    private fun message(role: String, content: String) = JsonObject().apply {
        addProperty("role", role)
        addProperty("content", content)
    }

    private fun addUi(role: String, content: String) {
        _messages.value = _messages.value + OpenCodeMessage(role = role, content = content)
    }

    private fun updateLastUi(content: String) {
        val current = _messages.value
        if (current.isEmpty()) return
        _messages.value = current.dropLast(1) + current.last().copy(content = content)
    }

    private fun removeLastUi() {
        _messages.value = _messages.value.dropLast(1)
    }

    fun stopProcessing() {
        streamJob?.cancel()
    }

    fun clearConversation() {
        stopProcessing()
        _messages.value = emptyList()
        conversation = JsonArray()
        autoApprove = false
        _error.value = null
    }

    companion object {
        private const val MAX_TOOL_ROUNDS = 15
        private const val UI_OUTPUT_LIMIT = 2_000
        private const val MODEL_OUTPUT_LIMIT = 30_000
        private const val SYSTEM_PROMPT =
            "Tu es un assistant qui fonctionne sur le téléphone Android de l'utilisateur, dans Termux " +
                "(HOME=/data/data/com.termux/files/home). Tu disposes des outils du serveur MCP Omni-Exec pour " +
                "exécuter des commandes et agir sur le système. Utilise-les quand l'utilisateur demande une action " +
                "ou une information système, puis résume clairement le résultat. Réponds en français."
    }
}
