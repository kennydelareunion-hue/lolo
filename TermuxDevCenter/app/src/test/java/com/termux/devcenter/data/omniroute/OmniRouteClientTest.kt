package com.termux.devcenter.data.omniroute

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class OmniRouteClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(apiKey: String = "sk-test", url: String = server.url("/").toString()) =
        OmniRouteClient(OmniRouteConfig(baseUrl = url, apiKey = apiKey))

    @Test
    fun `listModels parse la reponse et envoie la cle API`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"kr/claude-sonnet-4.5"},{"id":"gpt-4o"}]}"""))

        val models = client().listModels().getOrThrow()

        assertEquals(listOf("gpt-4o", "kr/claude-sonnet-4.5"), models)
        val request = server.takeRequest()
        assertEquals("/v1/models", request.path)
        assertEquals("Bearer sk-test", request.getHeader("Authorization"))
    }

    @Test
    fun `url du dashboard est normalisee`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))
        client(url = server.url("/dashboard/").toString()).listModels().getOrThrow()
        assertEquals("/v1/models", server.takeRequest().path)
    }

    @Test
    fun `streamChat assemble les fragments SSE`() = runBlocking {
        val sse = listOf(
            """data: {"choices":[{"delta":{"role":"assistant"}}]}""",
            """data: {"choices":[{"delta":{"content":"Bon"}}]}""",
            ": keep-alive",
            """data: {"choices":[{"delta":{"content":"jour"}}]}""",
            "data: [DONE]"
        ).joinToString("\n\n", postfix = "\n\n")
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(sse))

        val chunks = client().streamChat("kr/claude-sonnet-4.5", listOf(ChatMessage("user", "Salut"))).toList()

        assertEquals("Bonjour", chunks.joinToString(""))
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"stream\":true"))
        assertTrue(body.contains("\"model\":\"kr/claude-sonnet-4.5\""))
    }

    @Test
    fun `streamChat accepte une reponse JSON non streamee`() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"role":"assistant","content":"Salut !"}}]}""")
        )
        val chunks = client().streamChat("m", listOf(ChatMessage("user", "x"))).toList()
        assertEquals(listOf("Salut !"), chunks)
    }

    @Test
    fun `erreur 401 donne un message explicite`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Invalid API key"}}"""))
        try {
            client().streamChat("m", listOf(ChatMessage("user", "x"))).toList()
            fail("exception attendue")
        } catch (e: OmniRouteException) {
            assertEquals(401, e.httpCode)
            assertTrue(e.message!!.contains("Invalid API key"))
            assertTrue(e.message!!.contains("clé API"))
        }
    }

    @Test
    fun `serveur arrete donne un message de connexion`() = runBlocking {
        val url = server.url("/").toString()
        server.shutdown()
        val error = client(url = url).listModels().exceptionOrNull()
        assertTrue(error is OmniRouteException)
        assertTrue(error!!.message!!.contains("Impossible de joindre OmniRoute"))
    }
}
