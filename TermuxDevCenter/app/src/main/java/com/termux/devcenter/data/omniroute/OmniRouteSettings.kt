package com.termux.devcenter.data.omniroute

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class OmniRouteConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = "",
    val startCommand: String = DEFAULT_START_COMMAND,
    val mcpUrl: String = DEFAULT_MCP_URL,
    val mcpEnabled: Boolean = true,
    val confirmCommands: Boolean = true,
    val mcpStartCommand: String = DEFAULT_MCP_START_COMMAND,
    val favorites: Set<String> = setOf(ModelSelection.PREFERRED_MODEL),
    /** N'afficher que les modèles couverts par un compte connecté. */
    val connectedOnly: Boolean = true
) {
    /** URL sans slash final, utilisable pour construire les endpoints. */
    val normalizedBaseUrl: String
        get() = baseUrl.trim().trimEnd('/').removeSuffix("/v1").removeSuffix("/dashboard")
            .ifBlank { DEFAULT_BASE_URL }

    companion object {
        const val DEFAULT_BASE_URL = "http://localhost:20128"
        const val DEFAULT_START_COMMAND = "omniroute"
        const val DEFAULT_MCP_URL = "http://127.0.0.1:20129/mcp"
        const val DEFAULT_MCP_START_COMMAND = "node ~/.config/opencode/mcp-servers/omni-exec/http-server.js"
    }
}

private val Context.omniDataStore by preferencesDataStore(name = "omniroute")

class OmniRouteSettings(private val context: Context) {
    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val START_COMMAND = stringPreferencesKey("start_command")
        val MCP_URL = stringPreferencesKey("mcp_url")
        val MCP_ENABLED = booleanPreferencesKey("mcp_enabled")
        val CONFIRM_COMMANDS = booleanPreferencesKey("confirm_commands")
        val MCP_START_COMMAND = stringPreferencesKey("mcp_start_command")
        val FAVORITES = stringSetPreferencesKey("favorites")
        val CONNECTED_ONLY = booleanPreferencesKey("connected_only")
    }

    val config: Flow<OmniRouteConfig> = context.omniDataStore.data.map { p ->
        val d = OmniRouteConfig()
        OmniRouteConfig(
            baseUrl = p[Keys.BASE_URL] ?: d.baseUrl,
            apiKey = p[Keys.API_KEY] ?: d.apiKey,
            model = p[Keys.MODEL] ?: d.model,
            startCommand = p[Keys.START_COMMAND] ?: d.startCommand,
            mcpUrl = p[Keys.MCP_URL] ?: d.mcpUrl,
            mcpEnabled = p[Keys.MCP_ENABLED] ?: d.mcpEnabled,
            confirmCommands = p[Keys.CONFIRM_COMMANDS] ?: d.confirmCommands,
            mcpStartCommand = p[Keys.MCP_START_COMMAND] ?: d.mcpStartCommand,
            favorites = p[Keys.FAVORITES] ?: d.favorites,
            connectedOnly = p[Keys.CONNECTED_ONLY] ?: d.connectedOnly
        )
    }

    suspend fun current(): OmniRouteConfig = config.first()

    suspend fun save(config: OmniRouteConfig) {
        context.omniDataStore.edit {
            it[Keys.BASE_URL] = config.baseUrl.trim()
            it[Keys.API_KEY] = config.apiKey.trim()
            it[Keys.START_COMMAND] = config.startCommand.trim()
            it[Keys.MCP_URL] = config.mcpUrl.trim()
            it[Keys.MCP_ENABLED] = config.mcpEnabled
            it[Keys.CONFIRM_COMMANDS] = config.confirmCommands
            it[Keys.MCP_START_COMMAND] = config.mcpStartCommand.trim()
        }
    }

    suspend fun setModel(model: String) {
        context.omniDataStore.edit { it[Keys.MODEL] = model }
    }

    suspend fun toggleFavorite(model: String) {
        context.omniDataStore.edit {
            val current = it[Keys.FAVORITES] ?: OmniRouteConfig().favorites
            it[Keys.FAVORITES] = if (model in current) current - model else current + model
        }
    }

    suspend fun setConnectedOnly(value: Boolean) {
        context.omniDataStore.edit { it[Keys.CONNECTED_ONLY] = value }
    }
}
