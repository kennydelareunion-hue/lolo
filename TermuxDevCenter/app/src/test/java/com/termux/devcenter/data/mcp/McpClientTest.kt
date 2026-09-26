package com.termux.devcenter.data.mcp

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class McpClientTest {
    private lateinit var server: MockWebServer
    private val methods = mutableListOf<String>()
    private val sessionHeaders = mutableListOf<String?>()

    @Before
    fun setUp() {
        server = MockWebServer()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Serveur MCP minimal ; `sse` pour répondre en text/event-stream comme le SDK officiel. */
    private fun fakeMcp(sse: Boolean, supportsInitialize: Boolean = true) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
                val method = body.get("method").asString
                methods += method
                sessionHeaders += request.getHeader("Mcp-Session-Id")
                if (!body.has("id")) return MockResponse().setResponseCode(202)
                val id = body.get("id").asLong
                val result = when (method) {
                    "initialize" -> if (supportsInitialize) """{"protocolVersion":"2025-03-26","capabilities":{}}""" else null
                    "tools/list" -> """{"tools":[{"name":"run_command","description":"Exécute bash","inputSchema":{"type":"object","properties":{"command":{"type":"string"}}}}]}"""
                    "tools/call" -> {
                        val cmd = body.getAsJsonObject("params").getAsJsonObject("arguments").get("command").asString
                        """{"content":[{"type":"text","text":"sortie de $cmd"}],"isError":false}"""
                    }
                    else -> null
                }
                val msg = if (result != null) """{"jsonrpc":"2.0","id":$id,"result":$result}"""
                else """{"jsonrpc":"2.0","id":$id,"error":{"code":-32601,"message":"Method not found"}}"""
                val response = MockResponse().setHeader("Mcp-Session-Id", "sess-1")
                return if (sse) response.setHeader("Content-Type", "text/event-stream")
                    .setBody("event: message\ndata: $msg\n\n")
                else response.setHeader("Content-Type", "application/json").setBody(msg)
            }
        }
        server.start()
    }

    private fun client() = McpClient(server.url("/mcp").toString())

    @Test
    fun `handshake puis liste et appel d'outil en SSE`() = runBlocking {
        fakeMcp(sse = true)
        val c = client()

        val tools = c.listTools().getOrThrow()
        assertEquals(listOf("run_command"), tools.map { it.name })
        assertTrue(tools[0].inputSchema.getAsJsonObject("properties").has("command"))

        val result = c.callTool("run_command", JsonObject().apply { addProperty("command", "whoami") }).getOrThrow()
        assertEquals("sortie de whoami", result.text)
        assertFalse(result.isError)

        assertEquals(listOf("initialize", "notifications/initialized", "tools/list", "tools/call"), methods)
        // La session renvoyée par le serveur est réutilisée ensuite.
        assertEquals(null, sessionHeaders[0])
        assertEquals("sess-1", sessionHeaders[3])
    }

    @Test
    fun `serveur JSON sans initialize reste utilisable`() = runBlocking {
        fakeMcp(sse = false, supportsInitialize = false)
        val tools = client().listTools().getOrThrow()
        assertEquals(1, tools.size)
    }

    @Test
    fun `serveur arrete donne un message clair`() = runBlocking {
        server.start()
        val url = server.url("/mcp").toString()
        server.shutdown()
        val error = McpClient(url).listTools().exceptionOrNull()
        assertTrue(error is McpException)
        assertTrue(error!!.message!!.contains("Impossible de joindre Omni-Exec"))
    }
}
