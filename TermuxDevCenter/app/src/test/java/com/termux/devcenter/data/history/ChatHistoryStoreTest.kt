package com.termux.devcenter.data.history

import com.termux.devcenter.data.model.ModelLabel
import com.termux.devcenter.data.model.OpenCodeMessage
import com.termux.devcenter.data.omniroute.ModelTier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChatHistoryStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun record(id: String, updatedAt: Long) = ChatRecord(
        id = id,
        title = "Titre $id",
        modelId = "kr/claude-sonnet-4.5",
        modelName = "claude-sonnet-4.5",
        provider = "kiro",
        createdAt = 1,
        updatedAt = updatedAt,
        messages = listOf(
            OpenCodeMessage("user", "Salut", attachments = listOf("📄 notes.txt")),
            OpenCodeMessage("assistant", "Bonjour", model = ModelLabel("claude-sonnet-4.5", "kiro", ModelTier.FREE))
        ),
        conversation = """[{"role":"user","content":"Salut"},{"role":"assistant","content":"Bonjour"}]""",
        hopliteThreadId = null
    )

    @Test
    fun `sauvegarde recharge et persiste entre deux instances`() = runBlocking {
        val dir = tmp.newFolder("chats")
        val store = ChatHistoryStore(dir)
        store.save(record("a", 10))
        store.save(record("b", 20))
        store.setCurrent("a")

        val reopened = ChatHistoryStore(dir)
        assertEquals(listOf("b", "a"), reopened.summaries.value.map { it.id })
        assertEquals(2, reopened.summaries.value.first().messageCount)
        assertEquals("a", reopened.currentId())

        val loaded = reopened.load("a")!!
        assertEquals("kr/claude-sonnet-4.5", loaded.modelId)
        assertEquals(ModelTier.FREE, loaded.messages!![1].model!!.tier)
        assertEquals(listOf("📄 notes.txt"), loaded.messages!![0].attachments)
        assertEquals(record("a", 10).conversation, loaded.conversation)
    }

    @Test
    fun `suppression retire aussi la conversation courante`() = runBlocking {
        val store = ChatHistoryStore(tmp.newFolder("chats"))
        store.save(record("a", 10))
        store.setCurrent("a")
        store.delete("a")
        assertEquals(emptyList<ChatSummary>(), store.summaries.value)
        assertNull(store.currentId())
        assertNull(store.load("a"))
    }

    @Test
    fun `titre construit depuis le premier message`() {
        assertEquals("Bonjour Claude", ChatHistoryStore.titleFrom("\n  Bonjour Claude \nsuite"))
        assertEquals(60, ChatHistoryStore.titleFrom("x".repeat(100)).length)
        assertEquals("Conversation", ChatHistoryStore.titleFrom(" "))
    }
}
