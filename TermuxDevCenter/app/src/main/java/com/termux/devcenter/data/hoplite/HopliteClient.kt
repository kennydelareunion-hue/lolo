package com.termux.devcenter.data.hoplite

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.termux.devcenter.data.omniroute.ModelInfo
import com.termux.devcenter.data.omniroute.ModelTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

data class HopliteProject(val id: String, val name: String)

data class HopliteRunState(
    /** queued · running · waiting · completed · failed · cancelled */
    val status: String,
    val userMessageId: String?,
    val assistantContent: String?
)

data class HopliteThreadInfo(
    val status: String?,
    val runStatus: String?,
    val initializationPhase: String?,
    val initializationError: String?
)

class HopliteException(message: String, val httpCode: Int? = null) : Exception(message)

/**
 * Client de l'API publique Hoplite (https://hoplite.sh/docs/api), authentifiée par une clé `hop_…`.
 * Chaque conversation correspond à un thread Hoplite dans un projet : l'agent y travaille dans un sandbox
 * et consomme les crédits du compte.
 */
class HopliteClient(private val apiKey: String, private val baseUrl: String = DEFAULT_BASE_URL) {

    suspend fun listModels(): Result<List<ModelInfo>> = call {
        val body = get("/api/model-providers").asJsonObject
        body.getAsJsonArray("models").mapNotNull { el ->
            val m = el.asJsonObject
            val id = m.str("id") ?: return@mapNotNull null
            val vendor = m.str("provider").orEmpty()
            ModelInfo(
                id = ModelInfo.HOPLITE_PREFIX + id,
                provider = ModelInfo.HOPLITE_PROVIDER,
                upstreamId = id,
                tier = ModelTier.HOPLITE,
                name = listOfNotNull(m.str("name") ?: id, vendor.takeIf { it.isNotBlank() }).joinToString(" · ")
            )
        }
    }

    suspend fun listProjects(): Result<List<HopliteProject>> = call {
        val projects = mutableListOf<HopliteProject>()
        var cursor: String? = null
        do {
            val query = "?pagination=cursor&limit=50" + (cursor?.let { "&cursor=$it" } ?: "")
            val body = get("/api/projects$query").asJsonObject
            body.getAsJsonArray("projects")?.forEach { el ->
                val p = el.asJsonObject
                val id = p.str("id") ?: return@forEach
                projects += HopliteProject(id, p.str("name") ?: id)
            }
            cursor = body.str("nextCursor")
        } while (cursor != null && projects.size < 500)
        projects
    }

    /** Crée le thread avec le premier message ; renvoie l'identifiant du thread. */
    suspend fun createThread(projectId: String, prompt: String, model: String): Result<String> = call {
        val body = JsonObject().apply {
            addProperty("projectId", projectId)
            addProperty("prompt", prompt)
            addProperty("model", model)
            addProperty("title", prompt.lineSequence().first().take(80))
        }
        val thread = post("/api/threads", body).asJsonObject.getAsJsonObject("thread")
        thread.str("id") ?: throw HopliteException("Réponse Hoplite sans identifiant de thread")
    }

    /** Ajoute un message ; renvoie son identifiant pour reconnaître le run qu'il déclenche. */
    suspend fun sendMessage(threadId: String, content: String, model: String): Result<String> = call {
        val body = JsonObject().apply {
            addProperty("content", content)
            addProperty("model", model)
            addProperty("clientMessageId", UUID.randomUUID().toString())
        }
        val response = post("/api/threads/$threadId/messages", body).asJsonObject
        response.getAsJsonObject("message")?.str("id") ?: throw HopliteException("Réponse Hoplite sans message")
    }

    /** État du dernier run, ou `null` s'il n'a pas encore démarré. */
    suspend fun runState(threadId: String): Result<HopliteRunState?> = call {
        val body = try {
            get("/api/threads/$threadId/run-state").asJsonObject
        } catch (e: HopliteException) {
            if (e.httpCode == 404) return@call null else throw e
        }
        val run = body.getAsJsonObject("run") ?: return@call null
        HopliteRunState(
            status = run.str("status") ?: "queued",
            userMessageId = run.obj("userMessage")?.str("id"),
            assistantContent = run.obj("assistantMessage")?.str("content")
        )
    }

    suspend fun threadInfo(threadId: String): Result<HopliteThreadInfo> = call {
        val t = get("/api/threads/$threadId").asJsonObject.getAsJsonObject("thread")
        HopliteThreadInfo(
            status = t.str("status"),
            runStatus = t.str("runStatus"),
            initializationPhase = t.str("initializationPhase"),
            initializationError = t.str("initializationErrorMessage")
        )
    }

    private fun get(path: String): JsonElement =
        execute(Request.Builder().url(baseUrl + path).get())

    // Clé d'idempotence : une relance réseau ne crée pas de thread ou de message en double.
    private fun post(path: String, body: JsonObject): JsonElement =
        execute(
            Request.Builder().url(baseUrl + path)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .post(body.toString().toRequestBody(JSON))
        )

    private fun execute(builder: Request.Builder): JsonElement {
        if (apiKey.isBlank()) throw HopliteException("Clé API Hoplite manquante (Réglages).")
        val request = builder.header("X-Api-Key", apiKey.trim()).header("Accept", "application/json").build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw httpError(response.code, text)
            return JsonParser.parseString(text)
        }
    }

    private fun httpError(code: Int, body: String): HopliteException {
        val detail = runCatching {
            val o = JsonParser.parseString(body).asJsonObject
            o.str("message") ?: o.get("error")?.let { e -> if (e.isJsonObject) e.asJsonObject.str("message") else e.asString }
        }.getOrNull() ?: body.take(200)
        val hint = when (code) {
            401 -> " — clé API Hoplite invalide ou révoquée."
            402 -> " — crédits Hoplite insuffisants."
            403 -> " — la clé n'a pas la permission nécessaire."
            429 -> " — trop de requêtes, réessayez dans un instant."
            else -> ""
        }
        return HopliteException("Hoplite a répondu $code : $detail$hint", code)
    }

    private suspend fun <T> call(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        runCatching(block).recoverCatching {
            throw when (it) {
                is HopliteException -> it
                is IOException -> HopliteException("Impossible de joindre Hoplite : ${it.message ?: it.javaClass.simpleName}")
                else -> HopliteException("Réponse Hoplite inattendue : ${it.message}")
            }
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.hoplite.sh"
        const val WEB_URL = "https://app.hoplite.sh"
        private val JSON = "application/json".toMediaType()
        private val http = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        fun threadUrl(threadId: String) = "$WEB_URL/threads/$threadId"

        private fun JsonObject.str(key: String): String? =
            get(key)?.takeIf { it.isJsonPrimitive }?.asString

        private fun JsonObject.obj(key: String): JsonObject? =
            get(key)?.takeIf { it.isJsonObject }?.asJsonObject
    }
}
