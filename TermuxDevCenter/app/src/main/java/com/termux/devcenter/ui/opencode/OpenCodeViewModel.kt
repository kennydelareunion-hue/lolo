package com.termux.devcenter.ui.opencode

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.termux.devcenter.data.hoplite.HopliteClient
import com.termux.devcenter.data.hoplite.HopliteException
import com.termux.devcenter.data.mcp.McpClient
import com.termux.devcenter.data.mcp.McpTool
import com.termux.devcenter.data.model.ModelLabel
import com.termux.devcenter.data.model.OpenCodeMessage
import com.termux.devcenter.data.omniroute.FreeModelCatalog
import com.termux.devcenter.data.omniroute.ModelInfo
import com.termux.devcenter.data.omniroute.ModelSelection
import com.termux.devcenter.data.omniroute.ModelTier
import com.termux.devcenter.data.omniroute.ChatEvent
import com.termux.devcenter.data.omniroute.OmniRouteClient
import com.termux.devcenter.data.omniroute.OmniRouteConfig
import com.termux.devcenter.data.omniroute.OmniRouteSettings
import com.termux.devcenter.data.omniroute.ToolCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
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

    private val freeCatalog = FreeModelCatalog.load(application)

    /** Tous les modèles connus (OmniRoute + Hoplite), avant filtre par fournisseur. */
    private val _allModels = MutableStateFlow<List<ModelInfo>>(emptyList())

    /** Fournisseurs OmniRoute affichés : choix de l'utilisateur, sinon Kiro par défaut. */
    val enabledProviders: StateFlow<Set<String>> = combine(_allModels, settings.config) { all, config ->
        config.enabledProviders ?: ModelSelection.defaultProviders(all, freeCatalog)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Modèles proposés dans le sélecteur. */
    val models: StateFlow<List<ModelInfo>> = combine(_allModels, enabledProviders) { all, enabled ->
        ModelSelection.filterEnabled(all, enabled)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Fournisseurs OmniRoute disponibles avec leur nombre de modèles. */
    val providerCounts: StateFlow<List<Pair<String, Int>>> = _allModels.map { ModelSelection.providerCounts(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _hopliteStatus = MutableStateFlow<String?>(null)
    val hopliteStatus: StateFlow<String?> = _hopliteStatus.asStateFlow()

    val favorites: StateFlow<Set<String>> = settings.config.map { it.favorites }
        .stateIn(viewModelScope, SharingStarted.Eagerly, OmniRouteConfig().favorites)

    val connectedOnly: StateFlow<Boolean> = settings.config.map { it.connectedOnly }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

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
    /** Thread Hoplite de la conversation en cours (créé au premier message Hoplite). */
    private var hopliteThreadId: String? = null
    private var streamJob: Job? = null
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()

    init {
        viewModelScope.launch {
            settings.config.map { listOf(it.normalizedBaseUrl, it.apiKey, it.connectedOnly, it.hopliteApiKey) }
                .distinctUntilChanged()
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
            val omni = async { OmniRouteClient(config).listModelInfo(freeCatalog, config.connectedOnly) }
            val hoplite = async {
                if (config.hopliteApiKey.isBlank()) null else HopliteClient(config.hopliteApiKey).listModels()
            }
            val omniResult = omni.await()
            val hopliteResult = hoplite.await()

            _hopliteStatus.value = hopliteResult?.fold(
                onSuccess = { "Hoplite : ${it.size} modèle(s)" },
                onFailure = { "Hoplite indisponible : ${it.message}" }
            )
            val all = omniResult.getOrDefault(emptyList()) + hopliteResult?.getOrNull().orEmpty()
            _allModels.value = all

            val visible = ModelSelection.filterEnabled(
                all, config.enabledProviders ?: ModelSelection.defaultProviders(all, freeCatalog)
            )
            _error.value = omniResult.exceptionOrNull()?.message ?: if (visible.isEmpty()) {
                "Aucun modèle affiché : touchez le sélecteur de modèle › Fournisseurs pour en choisir, " +
                    "ou connectez un fournisseur dans l'onglet OmniRoute."
            } else null
            if (all.isNotEmpty()) {
                val chosen = config.model.takeIf { id -> visible.any { it.id == id } }
                    ?: ModelSelection.pickDefault(visible)?.id.orEmpty()
                _selectedModel.value = chosen
                if (chosen != config.model) settings.setModel(chosen)
            }
            _loadingModels.value = false
        }
    }

    fun toggleProvider(provider: String) {
        viewModelScope.launch {
            val current = enabledProviders.value
            settings.setEnabledProviders(if (provider in current) current - provider else current + provider)
        }
    }

    fun setAllProviders(enabled: Boolean) {
        viewModelScope.launch {
            settings.setEnabledProviders(if (enabled) providerCounts.value.map { it.first }.toSet() else emptySet())
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

    fun selectModel(model: String) {
        _selectedModel.value = model
        viewModelScope.launch { settings.setModel(model) }
    }

    fun toggleFavorite(model: String) {
        viewModelScope.launch { settings.toggleFavorite(model) }
    }

    fun setConnectedOnly(value: Boolean) {
        viewModelScope.launch { settings.setConnectedOnly(value) }
    }

    private fun labelFor(modelId: String): ModelLabel {
        val info = _allModels.value.firstOrNull { it.id == modelId }
        return ModelLabel(
            name = info?.displayName ?: modelId,
            provider = info?.provider ?: modelId.substringBefore('/', "?"),
            tier = info?.tier ?: ModelTier.PRO
        )
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
                if (model.startsWith(ModelInfo.HOPLITE_PREFIX)) runHoplite(model, prompt) else runAgentLoop(model)
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

    /**
     * Envoie le message à un agent Hoplite : un thread est créé au premier message puis réutilisé,
     * et l'état du run est interrogé jusqu'à la réponse finale.
     */
    private suspend fun runHoplite(model: String, prompt: String) {
        val config = settings.current()
        val client = HopliteClient(config.hopliteApiKey)
        val upstream = model.removePrefix(ModelInfo.HOPLITE_PREFIX)
        addUi("assistant", "⏳ Envoi à Hoplite…", labelFor(model))

        val existing = hopliteThreadId
        val expectedMessageId: String?
        val threadId: String
        if (existing == null) {
            val projectId = config.hopliteProjectId.ifBlank {
                client.listProjects().getOrThrow().firstOrNull()?.id
                    ?: throw HopliteException("Aucun projet Hoplite accessible avec cette clé.")
            }
            threadId = client.createThread(projectId, prompt, upstream).getOrThrow()
            hopliteThreadId = threadId
            expectedMessageId = null
        } else {
            threadId = existing
            expectedMessageId = client.sendMessage(threadId, prompt, upstream).getOrThrow()
        }
        val link = "\n\n🔗 ${HopliteClient.threadUrl(threadId)}"

        val deadline = System.currentTimeMillis() + HOPLITE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(HOPLITE_POLL_MS)
            val run = client.runState(threadId).getOrThrow()
            // Tant que le run de notre message n'existe pas, run-state décrit encore le précédent.
            val ours = run != null && (expectedMessageId == null || run.userMessageId == expectedMessageId)
            if (!ours) {
                val info = client.threadInfo(threadId).getOrNull()
                info?.initializationError?.let { throw HopliteException("Hoplite n'a pas pu démarrer : $it") }
                updateLastUi("⏳ Hoplite prépare l'environnement… ${info?.initializationPhase?.let { phaseLabel(it) } ?: ""}")
                continue
            }
            when (run!!.status) {
                "completed" -> {
                    val answer = run.assistantContent?.takeIf { it.isNotBlank() } ?: "(réponse vide)"
                    updateLastUi(answer + link)
                    conversation.add(message("assistant", answer))
                    return
                }
                "failed", "cancelled" -> throw HopliteException(
                    "Le run Hoplite s'est terminé (${run.status}). Détails : ${HopliteClient.threadUrl(threadId)}"
                )
                "waiting" -> updateLastUi(
                    "⏸ Hoplite attend une action (approbation d'une commande ?). Ouvrez le thread pour répondre.$link"
                )
                else -> updateLastUi("⏳ Hoplite travaille… (${run.status})" + (run.assistantContent?.let { "\n\n$it" } ?: ""))
            }
        }
        throw HopliteException("Pas de réponse d'Hoplite après ${HOPLITE_TIMEOUT_MS / 60_000} min. Suivez le thread : ${HopliteClient.threadUrl(threadId)}")
    }

    private fun phaseLabel(phase: String) = when (phase) {
        "provisioning_workspace", "allocating_sandbox" -> "(création du sandbox)"
        "resolving_repository", "fetching_repository" -> "(récupération du dépôt)"
        "configuring_workspace" -> "(configuration)"
        "dispatching_run" -> "(démarrage de l'agent)"
        else -> ""
    }

    private suspend fun runAgentLoop(model: String) {
        val config = settings.current()
        val client = OmniRouteClient(config)
        val tools = if (config.mcpEnabled) _mcpTools.value else emptyList()
        val toolsJson = toOpenAiTools(tools)

        repeat(MAX_TOOL_ROUNDS) {
            addUi("assistant", "", labelFor(model))
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
        if (last?.role == "assistant" && (last.content.isEmpty() || last.content.startsWith("⏳"))) updateLastUi(placeholder)
        val lastRole = conversation.lastOrNull()?.asJsonObject?.get("role")?.asString
        if (lastRole == "user" || lastRole == "tool") conversation.add(message("assistant", placeholder))
    }

    private fun message(role: String, content: String) = JsonObject().apply {
        addProperty("role", role)
        addProperty("content", content)
    }

    private fun addUi(role: String, content: String, model: ModelLabel? = null) {
        _messages.value = _messages.value + OpenCodeMessage(role = role, content = content, model = model)
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
        hopliteThreadId = null
        autoApprove = false
        _error.value = null
    }

    companion object {
        private const val MAX_TOOL_ROUNDS = 15
        private const val HOPLITE_POLL_MS = 3_000L
        private const val HOPLITE_TIMEOUT_MS = 30 * 60_000L
        private const val UI_OUTPUT_LIMIT = 2_000
        private const val MODEL_OUTPUT_LIMIT = 30_000
        private const val SYSTEM_PROMPT =
            "Tu es un assistant qui fonctionne sur le téléphone Android de l'utilisateur, dans Termux " +
                "(HOME=/data/data/com.termux/files/home). Tu disposes des outils du serveur MCP Omni-Exec pour " +
                "exécuter des commandes et agir sur le système. Utilise-les quand l'utilisateur demande une action " +
                "ou une information système, puis résume clairement le résultat. Réponds en français."
    }
}
