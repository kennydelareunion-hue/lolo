package com.termux.devcenter.data.history

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.termux.devcenter.data.model.OpenCodeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class ChatSummary(
    val id: String,
    val title: String,
    val modelId: String,
    val modelName: String?,
    val provider: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int
)

/** Conversation complète : affichage + mémoire (historique au format OpenAI) + thread Hoplite. */
data class ChatRecord(
    val id: String,
    val title: String,
    val modelId: String,
    val modelName: String?,
    val provider: String?,
    val createdAt: Long,
    val updatedAt: Long,
    // Nullables : Gson peut laisser null un champ absent d'un ancien fichier.
    val messages: List<OpenCodeMessage>?,
    val conversation: String?,
    val hopliteThreadId: String?
) {
    fun summary() = ChatSummary(
        id, title, modelId, modelName, provider, createdAt, updatedAt,
        messages.orEmpty().count { it.role == "user" || it.role == "assistant" }
    )
}

/** Historique des conversations du Chat IA, stocké en JSON dans le stockage privé de l'app. */
class ChatHistoryStore(private val dir: File) {
    private val gson = Gson()
    private val lock = Mutex()
    private val indexFile = File(dir, "index.json")
    private val currentFile = File(dir, "current")

    private val _summaries = MutableStateFlow<List<ChatSummary>>(emptyList())
    val summaries: StateFlow<List<ChatSummary>> = _summaries.asStateFlow()

    init {
        dir.mkdirs()
        _summaries.value = readIndex()
    }

    suspend fun save(record: ChatRecord) = io {
        writeAtomic(recordFile(record.id), gson.toJson(record))
        val updated = (_summaries.value.filterNot { it.id == record.id } + record.summary()).sortedByDescending { it.updatedAt }
        writeAtomic(indexFile, gson.toJson(updated))
        _summaries.value = updated
    }

    suspend fun load(id: String): ChatRecord? = io {
        runCatching { gson.fromJson(recordFile(id).readText(), ChatRecord::class.java) }.getOrNull()
    }

    suspend fun delete(id: String) = io {
        recordFile(id).delete()
        val updated = _summaries.value.filterNot { it.id == id }
        writeAtomic(indexFile, gson.toJson(updated))
        _summaries.value = updated
        if (readCurrent() == id) currentFile.delete()
    }

    suspend fun currentId(): String? = io { readCurrent()?.takeIf { recordFile(it).exists() } }

    suspend fun setCurrent(id: String?) = io {
        if (id == null) currentFile.delete() else writeAtomic(currentFile, id)
    }

    private fun readCurrent(): String? = runCatching { currentFile.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

    // L'identifiant sert de nom de fichier : on refuse tout ce qui pourrait sortir du dossier.
    private fun recordFile(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9-]+"))) { "identifiant invalide" }
        return File(dir, "$id.json")
    }

    private fun readIndex(): List<ChatSummary> = runCatching {
        val type = object : TypeToken<List<ChatSummary>>() {}.type
        gson.fromJson<List<ChatSummary>>(indexFile.readText(), type).orEmpty().sortedByDescending { it.updatedAt }
    }.getOrDefault(emptyList())

    private fun writeAtomic(file: File, content: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { lock.withLock { block() } }

    companion object {
        @Volatile
        private var instance: ChatHistoryStore? = null

        fun get(context: Context): ChatHistoryStore = instance ?: synchronized(this) {
            instance ?: ChatHistoryStore(File(context.applicationContext.filesDir, "chats")).also { instance = it }
        }

        fun newId(): String = UUID.randomUUID().toString()

        fun titleFrom(firstUserMessage: String): String =
            firstUserMessage.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
                ?.let { if (it.length > 60) it.take(59) + "…" else it } ?: "Conversation"
    }
}

/** Demande d'ouverture d'une conversation depuis l'historique, consommée par le Chat IA. */
object ChatNavigation {
    const val NEW_CHAT = "new"
    val openRequest = MutableStateFlow<String?>(null)
}
