package com.termux.devcenter.data.omniroute

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelCatalogTest {
    private val catalog = FreeModelCatalog.parse(
        """{"providers":{"kiro":{"claude-sonnet-4.5":"recurring-monthly"},"groq":{"llama-3.3-70b":"recurring-daily"}}}"""
    )

    private fun entries(json: String) = JsonParser.parseString(json).asJsonArray.map { it.asJsonObject }

    @Test
    fun `classe pro gratuit et combo et retire les doublons canoniques`() {
        val models = OmniRouteClient.toModelInfo(
            entries(
                """[
                {"id":"kr/claude-sonnet-4.5","owned_by":"kiro","root":"claude-sonnet-4.5","parent":null},
                {"id":"kiro/claude-sonnet-4.5","owned_by":"kiro","root":"claude-sonnet-4.5","parent":"kr/claude-sonnet-4.5"},
                {"id":"cc/claude-opus-4-6","owned_by":"claude","root":"claude-opus-4-6","parent":null},
                {"id":"groq/llama-3.3-70b","owned_by":"groq","root":"llama-3.3-70b"},
                {"id":"openrouter/x:free","owned_by":"openrouter","root":"x:free"},
                {"id":"or/y","owned_by":"openrouter","root":"y","free":true},
                {"id":"auto/best","owned_by":"combo","root":"auto/best"}
                ]"""
            ),
            catalog
        )
        val byId = models.associateBy { it.id }
        assertEquals(6, models.size)
        assertEquals(ModelTier.FREE, byId.getValue("kr/claude-sonnet-4.5").tier)
        assertEquals(ModelTier.PRO, byId.getValue("cc/claude-opus-4-6").tier)
        assertEquals(ModelTier.FREE, byId.getValue("groq/llama-3.3-70b").tier)
        assertEquals(ModelTier.FREE, byId.getValue("openrouter/x:free").tier)
        assertEquals(ModelTier.FREE, byId.getValue("or/y").tier)
        assertEquals(ModelTier.COMBO, byId.getValue("auto/best").tier)
        assertEquals("kiro", byId.getValue("kr/claude-sonnet-4.5").provider)
    }

    @Test
    fun `Claude Sonnet 4_5 via Kiro est choisi par defaut`() {
        val models = listOf(
            ModelInfo("cc/claude-sonnet-4-6", "claude", "claude-sonnet-4-6", ModelTier.PRO),
            ModelInfo("kr/claude-sonnet-4.5", "kiro", "claude-sonnet-4.5", ModelTier.FREE),
            ModelInfo("gpt-4o", "openai", "gpt-4o", ModelTier.PRO)
        )
        assertEquals("kr/claude-sonnet-4.5", ModelSelection.pickDefault(models)?.id)
        assertEquals(listOf("claude", "kiro", "openai"), ModelSelection.groupByProvider(models).map { it.first })
        assertEquals(listOf("kr/claude-sonnet-4.5"), ModelSelection.search(models, "claude kiro").map { it.id })
    }

    @Test
    fun `le catalogue embarque reconnait Kiro`() {
        val file = File("src/main/assets/free_models.json")
        val embedded = FreeModelCatalog.parse(file.readText())
        assertTrue(embedded.isFree("kiro", "claude-sonnet-4.5", "kr/claude-sonnet-4.5"))
    }

    @Test
    fun `listModelInfo demande configuredOnly`() = kotlinx.coroutines.runBlocking {
        val server = okhttp3.mockwebserver.MockWebServer()
        server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("""{"data":[{"id":"kr/claude-sonnet-4.5","owned_by":"kiro","root":"claude-sonnet-4.5"}]}"""))
        server.start()
        try {
            val list = OmniRouteClient(OmniRouteConfig(baseUrl = server.url("/").toString()))
                .listModelInfo(catalog).getOrThrow()
            assertEquals(ModelTier.FREE, list.single().tier)
            assertEquals("/v1/models?configuredOnly=true", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }
}
