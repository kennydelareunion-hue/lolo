package com.termux.devcenter.data.omniroute

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: String, val content: String)

data class ToolCall(val id: String, val name: String, val arguments: String)

sealed interface ChatEvent {
    data class Text(val text: String) : ChatEvent
    data class ToolCalls(val calls: List<ToolCall>) : ChatEvent
}

class OmniRouteException(message: String, val httpCode: Int? = null) : Exception(message) {
    /** OmniRoute tourne mais refuse la requête faute de clé API valide. */
    val isAuthError: Boolean get() = httpCode == 401 || httpCode == 403
}

/** Client de l'API compatible OpenAI exposée par OmniRoute (`/v1/models`, `/v1/chat/completions`). */
class OmniRouteClient(private val config: OmniRouteConfig) {

    private val baseUrl = config.normalizedBaseUrl

    private fun Request.Builder.auth(): Request.Builder = apply {
        if (config.apiKey.isNotBlank()) header("Authorization", "Bearer ${config.apiKey}")
    }

    suspend fun listModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        fetchModelEntries(configuredOnly = false).map { list -> list.map { it.get("id").asString }.distinct().sorted() }
    }

    /**
     * Modèles utilisables avec catégorie Pro/Gratuit. `configuredOnly=true` demande à OmniRoute
     * de ne garder que les modèles couverts par un compte réellement connecté.
     */
    suspend fun listModelInfo(
        freeCatalog: FreeModelCatalog,
        configuredOnly: Boolean = true
    ): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        fetchModelEntries(configuredOnly).map { entries -> toModelInfo(entries, freeCatalog) }
    }

    private fun fetchModelEntries(configuredOnly: Boolean): Result<List<JsonObject>> =
        runCatching {
            val url = "$baseUrl/v1/models" + if (configuredOnly) "?configuredOnly=true" else ""
            val request = Request.Builder().url(url).auth().get().build()
            quickClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw httpError(response.code, body)
                val data = JsonParser.parseString(body).asJsonObject.getAsJsonArray("data")
                    ?: JsonArray()
                data.filter { it.isJsonObject && it.asJsonObject.get("id")?.isJsonPrimitive == true }
                    .map { it.asJsonObject }
            }
        }.recoverCatching { throw friendly(it) }

    /** Émet les fragments de texte de la réponse au fil du streaming SSE. */
    fun streamChat(model: String, messages: List<ChatMessage>): Flow<String> {
        val json = JsonArray().apply {
            messages.forEach { m ->
                add(JsonObject().apply {
                    addProperty("role", m.role)
                    addProperty("content", m.content)
                })
            }
        }
        return streamCompletion(model, json, null).filterIsInstance<ChatEvent.Text>().map { it.text }
    }

    /**
     * Streaming complet : fragments de texte puis, en fin de réponse, les appels d'outils
     * demandés par le modèle (format OpenAI `tool_calls`).
     */
    fun streamCompletion(model: String, messages: JsonArray, tools: JsonArray?): Flow<ChatEvent> = channelFlow {
        val payload = JsonObject().apply {
            addProperty("model", model)
            addProperty("stream", true)
            add("messages", messages)
            if (tools != null && tools.size() > 0) add("tools", tools)
        }
        val request = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .auth()
            .header("Accept", "text/event-stream")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        val call = streamClient.newCall(request)

        val reader = launch(Dispatchers.IO) {
            val toolCalls = sortedMapOf<Int, ToolCallBuilder>()
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw httpError(response.code, response.body?.string().orEmpty())
                    }
                    val source = response.body?.source() ?: throw OmniRouteException("Réponse vide d'OmniRoute")
                    if (response.header("Content-Type").orEmpty().contains("application/json")) {
                        // Serveur ayant ignoré stream=true : réponse complète en un bloc.
                        parseChunk(JsonParser.parseString(source.readUtf8()), toolCalls)?.let { send(ChatEvent.Text(it)) }
                    } else {
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            val data = line.removePrefix("data:").trim()
                            if (data == "[DONE]") break
                            val json = runCatching { JsonParser.parseString(data) }.getOrNull() ?: continue
                            parseChunk(json, toolCalls)?.let { send(ChatEvent.Text(it)) }
                        }
                    }
                }
                if (toolCalls.isNotEmpty()) {
                    send(ChatEvent.ToolCalls(toolCalls.entries.map { (index, b) -> b.build(index) }))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (call.isCanceled()) throw CancellationException("annulé")
                throw friendly(e)
            }
        }
        try {
            reader.join()
        } finally {
            call.cancel()
        }
    }

    private class ToolCallBuilder {
        var id: String? = null
        val name = StringBuilder()
        val arguments = StringBuilder()

        fun build(index: Int) = ToolCall(id ?: "call_$index", name.toString(), arguments.toString())
    }

    /** Renvoie le texte du fragment et accumule les morceaux d'appels d'outils. */
    private fun parseChunk(json: JsonElement, toolCalls: MutableMap<Int, ToolCallBuilder>): String? {
        if (!json.isJsonObject) return null
        val obj = json.asJsonObject
        obj.get("error")?.takeIf { !it.isJsonNull }?.let { err ->
            val msg = if (err.isJsonObject) err.asJsonObject.get("message")?.asString else err.asString
            throw OmniRouteException(msg ?: err.toString())
        }
        val choice = obj.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject ?: return null
        val delta = choice.getAsJsonObject("delta") ?: choice.getAsJsonObject("message") ?: return null

        delta.getAsJsonArray("tool_calls")?.forEachIndexed { position, el ->
            val tc = el.asJsonObject
            val index = tc.get("index")?.asInt ?: position
            val builder = toolCalls.getOrPut(index) { ToolCallBuilder() }
            tc.get("id")?.takeIf { !it.isJsonNull }?.asString?.let { builder.id = it }
            tc.getAsJsonObject("function")?.let { fn ->
                fn.get("name")?.takeIf { !it.isJsonNull }?.asString?.let { name ->
                    // Certains proxys répètent le nom complet à chaque fragment.
                    if (builder.name.toString() != name) builder.name.append(name)
                }
                fn.get("arguments")?.takeIf { !it.isJsonNull }?.let { args ->
                    builder.arguments.append(if (args.isJsonPrimitive) args.asString else args.toString())
                }
            }
        }
        return delta.get("content")?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString
            ?.takeIf { it.isNotEmpty() }
    }

    private fun httpError(code: Int, body: String): OmniRouteException {
        val serverMessage = runCatching {
            val err = JsonParser.parseString(body).asJsonObject.get("error")
            if (err.isJsonObject) err.asJsonObject.get("message").asString else err.asString
        }.getOrNull() ?: body.take(300)
        val hint = when (code) {
            401, 403 -> "\nOmniRoute est bien démarré mais demande une clé API : créez-la dans l'onglet " +
                "OmniRoute (icône clé) puis collez-la dans Réglages."
            404 -> "\nModèle ou endpoint introuvable : rafraîchissez la liste des modèles."
            else -> ""
        }
        return OmniRouteException("OmniRoute a répondu $code : $serverMessage$hint", code)
    }

    private fun friendly(e: Throwable): Throwable = when (e) {
        is OmniRouteException -> e
        is ConnectException -> OmniRouteException(
            "Impossible de joindre OmniRoute sur $baseUrl. Vérifiez qu'il est démarré dans Termux."
        )
        is IOException -> OmniRouteException("Erreur réseau : ${e.message ?: e.javaClass.simpleName}")
        else -> e
    }

    companion object {
        /**
         * OmniRoute annonce chaque modèle deux fois (alias court « kr/… » et doublon « kiro/… »
         * dont `parent` pointe vers l'alias) : seuls les alias sont gardés.
         */
        fun toModelInfo(entries: List<JsonObject>, freeCatalog: FreeModelCatalog): List<ModelInfo> {
            val ids = entries.map { it.get("id").asString }.toSet()
            return entries.filter { e ->
                val parent = e.get("parent")?.takeIf { it.isJsonPrimitive }?.asString
                parent == null || parent !in ids
            }.map { e ->
                val id = e.get("id").asString
                val owner = e.get("owned_by")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: id.substringBefore('/', "autre")
                val root = e.get("root")?.takeIf { it.isJsonPrimitive }?.asString ?: id.substringAfter('/')
                val tier = when {
                    owner == "combo" -> ModelTier.COMBO
                    e.get("free")?.takeIf { it.isJsonPrimitive }?.asBoolean == true -> ModelTier.FREE
                    freeCatalog.isFree(owner, root, id) -> ModelTier.FREE
                    else -> ModelTier.PRO
                }
                ModelInfo(id = id, provider = owner, upstreamId = root, tier = tier)
            }.distinctBy { it.id }
        }

        private val JSON = "application/json".toMediaType()

        private val quickClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        // Les réponses des modèles peuvent être longues : pas de limite de lecture globale.
        private val streamClient = quickClient.newBuilder()
            .readTimeout(5, TimeUnit.MINUTES)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}
