package com.termux.devcenter.data.hoplite

import com.google.gson.JsonParser
import com.termux.devcenter.data.omniroute.ModelTier
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HopliteClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client() = HopliteClient("hop_test", server.url("/").toString().trimEnd('/'))

    @Test
    fun `modeles et projets avec la cle en en-tete`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ok":true,"models":[{"id":"claude-sonnet-5","name":"Sonnet 5","provider":"Anthropic"}]}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true,"projects":[{"id":"p1","name":"lolo","previewPort":null}],"nextCursor":"c2"}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true,"projects":[{"id":"p2","name":"autre","previewPort":null}],"nextCursor":null}"""))

        val models = client().listModels().getOrThrow()
        assertEquals("hoplite:claude-sonnet-5", models.single().id)
        assertEquals("Sonnet 5 · Anthropic", models.single().displayName)
        assertEquals(ModelTier.HOPLITE, models.single().tier)

        val projects = client().listProjects().getOrThrow()
        assertEquals(listOf("lolo", "autre"), projects.map { it.name })

        val first = server.takeRequest()
        assertEquals("/api/model-providers", first.path)
        assertEquals("hop_test", first.getHeader("X-Api-Key"))
        server.takeRequest()
        assertTrue(server.takeRequest().path!!.contains("cursor=c2"))
    }

    @Test
    fun `creation de thread puis message avec idempotence`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ok":true,"thread":{"id":"t1","projectId":"p1","status":"queued"}}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true,"message":{"id":"m2"},"queued":false,"run":{"id":"r2"}}"""))

        assertEquals("t1", client().createThread("p1", "Bonjour", "claude-sonnet-5").getOrThrow())
        assertEquals("m2", client().sendMessage("t1", "Suite", "claude-sonnet-5").getOrThrow())

        val create = server.takeRequest()
        assertEquals("/api/threads", create.path)
        assertNotNull(create.getHeader("Idempotency-Key"))
        val body = JsonParser.parseString(create.body.readUtf8()).asJsonObject
        assertEquals("p1", body.get("projectId").asString)
        assertEquals("claude-sonnet-5", body.get("model").asString)
        assertEquals("/api/threads/t1/messages", server.takeRequest().path)
    }

    @Test
    fun `etat du run et run absent`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"not found"}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"ok":true,"run":{"runId":"r1","status":"completed","finishedAt":"x",
                "assistantMessage":{"id":"a","threadId":"t1","role":"assistant","content":"Fait !","createdAt":"x"},
                "userMessage":{"id":"m1","threadId":"t1","role":"user","content":"q","createdAt":"x"}}}"""
            )
        )
        assertNull(client().runState("t1").getOrThrow())
        val state = client().runState("t1").getOrThrow()!!
        assertEquals("completed", state.status)
        assertEquals("m1", state.userMessageId)
        assertEquals("Fait !", state.assistantContent)
    }

    @Test
    fun `cle refusee donne un message clair`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"ok":false,"error":"Unauthorized"}"""))
        val e = client().listModels().exceptionOrNull() as HopliteException
        assertEquals(401, e.httpCode)
        assertTrue(e.message!!.contains("invalide"))
    }
}
