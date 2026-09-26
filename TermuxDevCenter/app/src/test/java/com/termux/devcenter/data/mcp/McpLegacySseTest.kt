package com.termux.devcenter.data.mcp

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Reproduit le serveur Omni-Exec de Termux : transport HTTP+SSE historique qui répond
 * « 400 Missing sessionId » à un POST direct. Mini serveur HTTP sur socket
 * (com.sun.net.httpserver n'est pas disponible dans les tests Android).
 */
class McpLegacySseTest {
    private lateinit var server: ServerSocket
    private val streams = ConcurrentHashMap<String, Pair<OutputStream, CountDownLatch>>()
    private val methods = Collections.synchronizedList(mutableListOf<String>())
    private val sessionCounter = AtomicInteger()
    private val directPosts = AtomicInteger()

    @Before
    fun setUp() {
        server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { handle(socket) }; runCatching { socket.close() } }
            }
        }
    }

    @After
    fun tearDown() {
        streams.values.forEach { it.second.countDown() }
        server.close()
    }

    private fun handle(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        val out = socket.getOutputStream()
        // Connexions keep-alive d'OkHttp : plusieurs requêtes par socket.
        while (true) {
            val requestLine = reader.readLine() ?: return
            if (requestLine.isEmpty()) continue
            val (method, target) = requestLine.split(" ").let { it[0] to it[1] }
            var length = 0
            while (true) {
                val h = reader.readLine() ?: return
                if (h.isEmpty()) break
                if (h.startsWith("Content-Length:", true)) length = h.substringAfter(':').trim().toInt()
            }
            val buf = CharArray(length)
            var read = 0
            while (read < length) read += reader.read(buf, read, length - read)
            if (method == "GET") return openStream(out)
            handlePost(out, target, String(buf))
        }
    }

    private fun openStream(out: OutputStream) {
        val sid = "s${sessionCounter.incrementAndGet()}"
        val latch = CountDownLatch(1)
        synchronized(out) {
            out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n".toByteArray())
            out.write("event: endpoint\ndata: /mcp?sessionId=$sid\n\n".toByteArray())
            out.flush()
        }
        streams[sid] = out to latch
        latch.await()
        streams.remove(sid)
    }

    private fun handlePost(out: OutputStream, target: String, body: String) {
        val sid = target.substringAfter("sessionId=", "").takeIf { it.isNotEmpty() }
        if (sid == null) {
            directPosts.incrementAndGet()
            return respond(out, 400, "Missing sessionId")
        }
        val stream = streams[sid] ?: return respond(out, 404, "Session not found")
        val msg = JsonParser.parseString(body).asJsonObject
        val method = msg.get("method").asString
        methods += method
        respond(out, 202, "Accepted")
        val id = msg.get("id")?.asLong ?: return
        val result = when (method) {
            "initialize" -> """{"protocolVersion":"2024-11-05","capabilities":{"tools":{}}}"""
            "tools/list" -> """{"tools":[{"name":"run_long_command","description":"bash","inputSchema":{"type":"object","properties":{"command":{"type":"string"}}}}]}"""
            "tools/call" -> {
                val cmd = msg.getAsJsonObject("params").getAsJsonObject("arguments").get("command").asString
                """{"content":[{"type":"text","text":"ok: $cmd"}]}"""
            }
            else -> "{}"
        }
        val sse = stream.first
        synchronized(sse) {
            sse.write("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":$id,\"result\":$result}\n\n".toByteArray())
            sse.flush()
        }
    }

    private fun respond(out: OutputStream, code: Int, text: String) {
        val bytes = text.toByteArray()
        synchronized(out) {
            out.write("HTTP/1.1 $code X\r\nContent-Type: text/plain\r\nContent-Length: ${bytes.size}\r\n\r\n".toByteArray())
            out.write(bytes)
            out.flush()
        }
    }

    private fun url() = "http://127.0.0.1:${server.localPort}/mcp"

    @Test
    fun `bascule automatiquement sur le transport SSE`() = runBlocking {
        val client = McpClient(url())

        val tools = client.listTools().getOrThrow()
        assertEquals(listOf("run_long_command"), tools.map { it.name })

        val result = client.callTool("run_long_command", JsonObject().apply { addProperty("command", "id") })
            .getOrThrow()
        assertEquals("ok: id", result.text)
        assertEquals(listOf("initialize", "notifications/initialized", "tools/list", "tools/call"), methods)
        assertEquals(1, directPosts.get())
    }

    @Test
    fun `se reconnecte apres coupure du flux`() = runBlocking {
        val client = McpClient(url())
        client.listTools().getOrThrow()

        // Serveur redémarré : l'ancienne session disparaît.
        streams.values.forEach { it.second.countDown() }
        Thread.sleep(300)

        val result = client.callTool("run_long_command", JsonObject().apply { addProperty("command", "ls") })
            .getOrThrow()
        assertEquals("ok: ls", result.text)
        assertEquals(2, sessionCounter.get())
        // Transport déjà connu : pas de nouvel essai en POST direct.
        assertEquals(1, directPosts.get())
        assertTrue(methods.count { it == "initialize" } == 2)
    }
}
