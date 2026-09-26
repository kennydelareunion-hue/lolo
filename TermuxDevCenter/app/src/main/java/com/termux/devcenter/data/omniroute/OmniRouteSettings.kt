package com.termux.devcenter.data.omniroute

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class OmniRouteConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = "",
    val startCommand: String = DEFAULT_START_COMMAND
) {
    /** URL sans slash final, utilisable pour construire les endpoints. */
    val normalizedBaseUrl: String
        get() = baseUrl.trim().trimEnd('/').removeSuffix("/v1").removeSuffix("/dashboard")
            .ifBlank { DEFAULT_BASE_URL }

    companion object {
        const val DEFAULT_BASE_URL = "http://localhost:20128"
        const val DEFAULT_START_COMMAND = "omniroute"
    }
}

private val Context.omniDataStore by preferencesDataStore(name = "omniroute")

class OmniRouteSettings(private val context: Context) {
    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val START_COMMAND = stringPreferencesKey("start_command")
    }

    val config: Flow<OmniRouteConfig> = context.omniDataStore.data.map { p ->
        OmniRouteConfig(
            baseUrl = p[Keys.BASE_URL] ?: OmniRouteConfig.DEFAULT_BASE_URL,
            apiKey = p[Keys.API_KEY] ?: "",
            model = p[Keys.MODEL] ?: "",
            startCommand = p[Keys.START_COMMAND] ?: OmniRouteConfig.DEFAULT_START_COMMAND
        )
    }

    suspend fun current(): OmniRouteConfig = config.first()

    suspend fun save(baseUrl: String, apiKey: String, startCommand: String) {
        context.omniDataStore.edit {
            it[Keys.BASE_URL] = baseUrl.trim()
            it[Keys.API_KEY] = apiKey.trim()
            it[Keys.START_COMMAND] = startCommand.trim()
        }
    }

    suspend fun setModel(model: String) {
        context.omniDataStore.edit { it[Keys.MODEL] = model }
    }
}
