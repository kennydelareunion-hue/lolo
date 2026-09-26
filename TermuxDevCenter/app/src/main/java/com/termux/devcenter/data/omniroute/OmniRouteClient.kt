package com.termux.devcenter.data.omniroute

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: String, val content: String)

class OmniRouteException(message: String, val httpCode: Int? = null) : Exception(message)

/** Client de l'API compatible OpenAI exposée par OmniRoute (`/v1/models`, `/v1/chat/completions`). */
class OmniRouteClient(private val config: OmniRouteConfig) {

    private val baseUrl = config.normalizedBaseUrl

    private fun Request.Builder.auth(): Request.Builder = apply {
        if (config.apiKey.isNotBlank()) header("Authorization", "Bearer ${config.apiKey}")
    }

    suspend fun listModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("$baseUrl/v1/models").auth().get().build()
            quickClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw httpError(response.code, body)
                val data = JsonParser.parseString(body).asJsonObject.getAsJsonArray("data")
                    ?: JsonArray()
                data.mapNotNull { it.asJsonObject.get("id")?.asString }.distinct().sorted()
            }
        }.recoverCatching { throw friendly(it) }
    }

    /** Émet les fragments de texte de la réponse au fil du streaming SSE. */
    fun streamChat(model: String, messages: List<ChatMessage>): Flow<String> = callbackFlow {
        val payload = JsonObject().apply {
            addProperty("model", model)
            addProperty("stream", true)
            add("messages", JsonArray().apply {
                messages.forEach { m ->
                    add(JsonObject().apply {
                        addProperty("role", m.role)
                        addProperty("content", m.content)
                    })
                }
            })
        }
        val request = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .auth()
            .header("Accept", "text/event-stream")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        val call = streamClient.newCall(request)

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw httpError(response.code, response.body?.string().orEmpty())
                }
                val source = response.body?.source() ?: throw OmniRouteException("Réponse vide d'OmniRoute")
                val isJson = response.header("Content-Type").orEmpty().contains("application/json")
                if (isJson) {
                    // Serveur ayant ignoré stream=true : réponse complète en un bloc.
                    extractMessageContent(source.readUtf8())?.let { trySend(it) }
                } else {
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        extractDeltaContent(data)?.let { trySend(it) }
                    }
                }
            }
            close()
        } catch (e: Throwable) {
            close(if (call.isCanceled()) null else friendly(e))
        }
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    private fun extractDeltaContent(data: String): String? = runCatching {
        val obj = JsonParser.parseString(data).asJsonObject
        obj.getAsJsonObject("error")?.let { throw OmniRouteException(it.get("message")?.asString ?: data) }
        val choice = obj.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject ?: return null
        val delta = choice.getAsJsonObject("delta") ?: choice.getAsJsonObject("message")
        delta?.get("content")?.takeIf { !it.isJsonNull }?.asString
    }.getOrElse { if (it is OmniRouteException) throw it else null }

    private fun extractMessageContent(body: String): String? = runCatching {
        JsonParser.parseString(body).asJsonObject.getAsJsonArray("choices")
            ?.firstOrNull()?.asJsonObject?.getAsJsonObject("message")?.get("content")?.asString
    }.getOrNull()

    private fun httpError(code: Int, body: String): OmniRouteException {
        val serverMessage = runCatching {
            val err = JsonParser.parseString(body).asJsonObject.get("error")
            if (err.isJsonObject) err.asJsonObject.get("message").asString else err.asString
        }.getOrNull() ?: body.take(300)
        val hint = when (code) {
            401, 403 -> "\nVérifiez la clé API dans Paramètres (créez-la dans OmniRoute > API Keys)."
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
