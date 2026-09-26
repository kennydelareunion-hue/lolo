package com.termux.devcenter.data.mcp

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

data class McpTool(val name: String, val description: String, val inputSchema: JsonObject)

data class McpToolResult(val text: String, val isError: Boolean)

class McpException(
    message: String,
    /** Code HTTP (positif) ou code d'erreur JSON-RPC (négatif). */
    val code: Int? = null,
    /** La session côté serveur est perdue : il faut se reconnecter. */
    val sessionLost: Boolean = false
) : Exception(message)

/**
 * Client MCP pour le serveur Omni-Exec de Termux. Deux transports sont gérés :
 * - « Streamable HTTP » (POST JSON-RPC, réponse JSON ou SSE) ;
 * - « HTTP+SSE » historique (GET ouvre un flux qui annonce l'endpoint `?sessionId=…`,
 *   les requêtes sont postées dessus et les réponses reviennent par le flux).
 * Le transport est détecté automatiquement.
 */
class McpClient(val url: String) {
    private enum class Mode { UNKNOWN, STREAMABLE, LEGACY_SSE }

    private val mutex = Mutex()
    private val ids = AtomicLong(1)
    private var sessionId: String? = null
    private var protocolVersion: String? = null
    private var mode = Mode.UNKNOWN
    private var legacyDetected = false
    private var sse: LegacySseSession? = null

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

    // runInterruptible : l'arrêt d'une conversation débloque l'attente d'une réponse SSE.
    private suspend fun <T> run(block: () -> T): Result<T> = try {
        Result.success(mutex.withLock { runInterruptible(Dispatchers.IO) { ensureInitialized(); block() } })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(friendly(e))
    }

    private fun ensureInitialized() {
        if (mode != Mode.UNKNOWN) return
        if (legacyDetected) return connectLegacy()
        try {
            mode = Mode.STREAMABLE
            handshake()
        } catch (e: McpException) {
            val code = e.code ?: 0
            when {
                // Erreur JSON-RPC : serveur simplifié sans handshake, on garde ce transport.
                code < 0 -> Unit
                // Ex. « 400 Missing sessionId » : serveur en transport HTTP+SSE historique.
                code in 400..499 && code != 401 && code != 403 -> {
                    mode = Mode.UNKNOWN
                    try {
                        connectLegacy()
                    } catch (legacy: Exception) {
                        throw McpException("${e.message}\n(transport SSE également refusé : ${legacy.message})", e.code)
                    }
                }
                else -> {
                    mode = Mode.UNKNOWN
                    throw e
                }
            }
        } catch (e: Exception) {
            mode = Mode.UNKNOWN
            throw e
        }
    }

    private fun connectLegacy() {
        val session = LegacySseSession.open(url)
        sse = session
        mode = Mode.LEGACY_SSE
        legacyDetected = true
        try {
            handshake()
        } catch (e: McpException) {
            if ((e.code ?: 0) >= 0) {
                reset()
                throw e
            }
        } catch (e: Exception) {
            reset()
            throw e
        }
    }

    private fun handshake() {
        val result = send("initialize", JsonObject().apply {
            addProperty("protocolVersion", PROTOCOL_VERSION)
            add("capabilities", JsonObject())
            add("clientInfo", JsonObject().apply {
                addProperty("name", "termux-dev-center")
                addProperty("version", "1.3.0")
            })
        })
        protocolVersion = result.get("protocolVersion")?.asString
        send("notifications/initialized", JsonObject(), notification = true)
    }

    private fun reset() {
        sse?.close()
        sse = null
        sessionId = null
        protocolVersion = null
        mode = Mode.UNKNOWN
    }

    private fun request(method: String, params: JsonObject): JsonObject = try {
        send(method, params)
    } catch (e: McpException) {
        val retry = when (mode) {
            Mode.STREAMABLE -> e.code == 404 && sessionId != null
            Mode.LEGACY_SSE -> e.sessionLost
            Mode.UNKNOWN -> false
        }
        if (!retry) throw e
        // Serveur redémarré ou flux coupé : nouvelle session puis une seule relance.
        reset()
        ensureInitialized()
        send(method, params)
    }

    private fun send(method: String, params: JsonObject, notification: Boolean = false): JsonObject {
        val id = if (notification) null else ids.getAndIncrement()
        val body = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            id?.let { addProperty("id", it) }
            addProperty("method", method)
            add("params", params)
        }
        val message = when (mode) {
            Mode.LEGACY_SSE -> checkNotNull(sse).rpc(body, id)
            else -> postStreamable(body, id)
        } ?: return JsonObject()
        return unwrap(message)
    }

    private fun postStreamable(body: JsonObject, id: Long?): JsonElement? {
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
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw httpError(response, text)
            if (id == null) return null
            return if (response.header("Content-Type").orEmpty().contains("text/event-stream")) {
                parseSse(text, id)
            } else {
                JsonParser.parseString(text)
            }
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

    /** Session du transport HTTP+SSE : un flux GET permanent + des POST vers l'endpoint annoncé. */
    private class LegacySseSession private constructor(private val call: Call, response: Response, base: HttpUrl) {
        private val pending = ConcurrentHashMap<Long, CompletableFuture<JsonElement>>()
        private val endpointFuture = CompletableFuture<HttpUrl>()

        @Volatile
        private var closed = false

        init {
            Thread({ readLoop(response, base) }, "omni-exec-sse").apply { isDaemon = true }.start()
        }

        private fun readLoop(response: Response, base: HttpUrl) {
            try {
                response.use {
                    val source = it.body!!.source()
                    var event = "message"
                    val data = StringBuilder()
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        when {
                            line.isEmpty() -> {
                                if (data.isNotEmpty()) dispatch(event, data.toString(), base)
                                event = "message"
                                data.clear()
                            }
                            line.startsWith(":") -> Unit
                            line.startsWith("event:") -> event = line.substringAfter(':').trim()
                            line.startsWith("data:") -> {
                                if (data.isNotEmpty()) data.append('\n')
                                data.append(line.substringAfter(':').removePrefix(" "))
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                shutdown()
            }
        }

        private fun dispatch(event: String, data: String, base: HttpUrl) {
            when (event) {
                "endpoint" -> base.resolve(data.trim())?.let { endpointFuture.complete(it) }
                "message" -> {
                    val msg = runCatching { JsonParser.parseString(data) }.getOrNull()
                        ?.takeIf { it.isJsonObject }?.asJsonObject ?: return
                    val id = msg.get("id")?.takeIf { it.isJsonPrimitive }?.asString?.toLongOrNull() ?: return
                    pending.remove(id)?.complete(msg)
                }
            }
        }

        private fun shutdown() {
            closed = true
            val error = McpException("Flux SSE d'Omni-Exec fermé", sessionLost = true)
            endpointFuture.completeExceptionally(error)
            pending.values.forEach { it.completeExceptionally(error) }
            pending.clear()
        }

        fun close() {
            call.cancel()
        }

        fun rpc(body: JsonObject, id: Long?): JsonElement? {
            if (closed) throw McpException("Flux SSE d'Omni-Exec fermé", sessionLost = true)
            val endpoint = await(endpointFuture, ENDPOINT_TIMEOUT_S, "l'annonce de session")
            val future = id?.let { CompletableFuture<JsonElement>().also { f -> pending[it] = f } }
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .post(body.toString().toRequestBody(JSON))
                    .build()
                http.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw McpException(
                            "Omni-Exec a répondu ${response.code} : ${text.take(300)}",
                            response.code,
                            sessionLost = response.code == 400 || response.code == 404
                        )
                    }
                    // Certains serveurs répondent directement dans le POST plutôt que dans le flux.
                    if (id != null && text.trimStart().startsWith("{")) {
                        val direct = runCatching { JsonParser.parseString(text) }.getOrNull()
                        if (direct != null && direct.isJsonObject && direct.asJsonObject.has("jsonrpc")) {
                            pending.remove(id)
                            return direct
                        }
                    }
                }
                return future?.let { await(it, RESPONSE_TIMEOUT_S, "la réponse") }
            } catch (e: Throwable) {
                id?.let { pending.remove(it) }
                throw e
            }
        }

        private fun <T> await(future: CompletableFuture<T>, seconds: Long, what: String): T = try {
            future.get(seconds, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        } catch (e: TimeoutException) {
            throw McpException("Omni-Exec n'a pas envoyé $what à temps.", sessionLost = true)
        }

        companion object {
            private const val ENDPOINT_TIMEOUT_S = 10L
            private const val RESPONSE_TIMEOUT_S = 600L

            fun open(url: String): LegacySseSession {
                val request = Request.Builder()
                    .url(url.toHttpUrl())
                    .header("Accept", "text/event-stream")
                    .get()
                    .build()
                val call = sseHttp.newCall(request)
                val response = call.execute()
                if (!response.isSuccessful ||
                    !response.header("Content-Type").orEmpty().contains("text/event-stream")
                ) {
                    val code = response.code
                    response.close()
                    throw McpException("le serveur ne propose pas de flux SSE sur $url (HTTP $code)", code)
                }
                val session = LegacySseSession(call, response, request.url)
                try {
                    session.await(session.endpointFuture, ENDPOINT_TIMEOUT_S, "l'annonce de session")
                } catch (e: Throwable) {
                    session.close()
                    throw e
                }
                return session
            }
        }
    }

    companion object {
        const val PROTOCOL_VERSION = "2025-03-26"
        private val JSON = "application/json".toMediaType()
        private val http = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES) // commandes longues (compilation…)
            .build()

        // Flux SSE permanent : aucune limite de lecture.
        private val sseHttp = http.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()

        private fun httpError(response: Response, text: String) =
            McpException("Omni-Exec a répondu ${response.code} : ${text.take(300)}", response.code)

        private val clients = ConcurrentHashMap<String, McpClient>()

        /** Instance partagée par URL pour conserver la session MCP. */
        fun forUrl(url: String): McpClient = clients.getOrPut(url.trim()) { McpClient(url.trim()) }
    }
}
