package com.termux.devcenter.data.mcp

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class McpTool(val name: String, val description: String, val inputSchema: JsonObject)

data class McpToolResult(val text: String, val isError: Boolean)

class McpException(message: String, val code: Int? = null) : Exception(message)

/**
 * Client MCP en transport HTTP (JSON-RPC 2.0, réponses JSON ou SSE),
 * utilisé pour le serveur Omni-Exec de Termux.
 */
class McpClient(val url: String) {
    private val mutex = Mutex()
    private val ids = AtomicLong(1)
    private var sessionId: String? = null
    private var protocolVersion: String? = null
    private var initialized = false

    suspend fun listTools(): Result<List<McpTool>> = run {
        val result = request("tools/list", JsonObject())
        result.getAsJsonArray("tools")?.mapNotNull { el ->
            val obj = el.asJsonObject
            val name = obj.get("name")?.asString ?: return@mapNotNull null
            McpTool(
                name = name,
                description = obj.get("description")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                inputSchema = obj.getAsJsonObject("inputSchema")
                    ?: JsonObject().apply { addProperty("type", "object"); add("properties", JsonObject()) }
            )
        } ?: emptyList()
    }

    suspend fun callTool(name: String, arguments: JsonObject): Result<McpToolResult> = run {
        val result = request("tools/call", JsonObject().apply {
            addProperty("name", name)
            add("arguments", arguments)
        })
        val content = result.getAsJsonArray("content")
        val text = content?.joinToString("\n") { item ->
            val obj = item.asJsonObject
            when (obj.get("type")?.asString) {
                "text" -> obj.get("text")?.asString.orEmpty()
                else -> "[${obj.get("type")?.asString ?: "contenu"}]"
            }
        } ?: result.get("structuredContent")?.toString() ?: result.toString()
        McpToolResult(text, result.get("isError")?.asBoolean == true)
    }

    private suspend fun <T> run(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        runCatching { mutex.withLock { ensureInitialized(); block() } }
            .recoverCatching { throw friendly(it) }
    }

    private fun ensureInitialized() {
        if (initialized) return
        try {
            val result = rpc("initialize", JsonObject().apply {
                addProperty("protocolVersion", PROTOCOL_VERSION)
                add("capabilities", JsonObject())
                add("clientInfo", JsonObject().apply {
                    addProperty("name", "termux-dev-center")
                    addProperty("version", "1.2.0")
                })
            })
            protocolVersion = result.get("protocolVersion")?.asString
            rpc("notifications/initialized", JsonObject(), notification = true)
        } catch (e: McpException) {
            // Serveur simplifié sans handshake : on tente quand même les appels directs.
        }
        initialized = true
    }

    private fun request(method: String, params: JsonObject): JsonObject = try {
        rpc(method, params)
    } catch (e: McpException) {
        // Session expirée (serveur redémarré) : nouveau handshake puis une seule relance.
        if (e.code == 404 && sessionId != null) {
            sessionId = null
            initialized = false
            ensureInitialized()
            rpc(method, params)
        } else throw e
    }

    private fun rpc(method: String, params: JsonObject, notification: Boolean = false): JsonObject {
        val id = if (notification) null else ids.getAndIncrement()
        val body = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            id?.let { addProperty("id", it) }
            addProperty("method", method)
            add("params", params)
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json, text/event-stream")
            .apply {
                sessionId?.let { header("Mcp-Session-Id", it) }
                protocolVersion?.let { header("MCP-Protocol-Version", it) }
            }
            .post(body.toString().toRequestBody(JSON))
            .build()

        http.newCall(request).execute().use { response ->
            response.header("Mcp-Session-Id")?.let { sessionId = it }
            if (notification) return JsonObject()
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw McpException("Omni-Exec a répondu ${response.code} : ${text.take(300)}", response.code)
            }
            val message = if (response.header("Content-Type").orEmpty().contains("text/event-stream")) {
                parseSse(text, id!!)
            } else {
                JsonParser.parseString(text)
            }
            return unwrap(message)
        }
    }

    private fun parseSse(text: String, id: Long): JsonElement {
        val events = text.lineSequence()
            .filter { it.startsWith("data:") }
            .mapNotNull { runCatching { JsonParser.parseString(it.removePrefix("data:").trim()) }.getOrNull() }
            .filter { it.isJsonObject }
            .toList()
        return events.firstOrNull { it.asJsonObject.get("id")?.asLong == id }
            ?: events.lastOrNull()
            ?: throw McpException("Réponse SSE vide d'Omni-Exec")
    }

    private fun unwrap(message: JsonElement): JsonObject {
        val obj = if (message.isJsonArray) message.asJsonArray.first().asJsonObject else message.asJsonObject
        obj.get("error")?.takeIf { !it.isJsonNull }?.let { err ->
            if (err.isJsonObject) {
                val e = err.asJsonObject
                throw McpException(e.get("message")?.asString ?: err.toString(), e.get("code")?.asInt)
            }
            throw McpException(err.toString())
        }
        val result = obj.get("result") ?: return obj
        return if (result.isJsonObject) result.asJsonObject
        else JsonObject().apply { add("structuredContent", result) }
    }

    private fun friendly(e: Throwable): Throwable = when (e) {
        is McpException -> e
        is ConnectException -> McpException(
            "Impossible de joindre Omni-Exec sur $url. Démarrez-le depuis l'Accueil ou vérifiez l'URL MCP."
        )
        is IOException -> McpException("Erreur réseau Omni-Exec : ${e.message ?: e.javaClass.simpleName}")
        is IllegalStateException, is com.google.gson.JsonParseException ->
            McpException("Réponse inattendue d'Omni-Exec (ce n'est peut-être pas l'endpoint MCP) : ${e.message}")
        else -> e
    }

    companion object {
        const val PROTOCOL_VERSION = "2025-03-26"
        private val JSON = "application/json".toMediaType()
        private val http = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES) // commandes longues (compilation…)
            .build()

        private val clients = ConcurrentHashMap<String, McpClient>()

        /** Instance partagée par URL pour conserver la session MCP. */
        fun forUrl(url: String): McpClient = clients.getOrPut(url.trim()) { McpClient(url.trim()) }
    }
}
