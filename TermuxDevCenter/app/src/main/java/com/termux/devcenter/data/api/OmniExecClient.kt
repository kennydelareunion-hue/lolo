package com.termux.devcenter.data.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OmniExecClient(
    private val baseUrl: String = "http://127.0.0.1:20129"
) {
    private val gson = Gson()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    suspend fun executeCommand(command: String, workdir: String? = null): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val json = JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                addProperty("id", System.currentTimeMillis())
                addProperty("method", "tools/call")
                add("params", JsonObject().apply {
                    addProperty("name", "run_long_command")
                    add("arguments", JsonObject().apply {
                        addProperty("command", command)
                        workdir?.let { addProperty("workdir", it) }
                    })
                })
            }

            val request = Request.Builder()
                .url("$baseUrl/mcp")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (response.isSuccessful && body != null) {
                val result = gson.fromJson(body, Map::class.java) as Map<String, Any>
                Result.success(result)
            } else {
                Result.failure(Exception("Command failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkHealth(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()

            client.newCall(request).execute().use { Result.success(it.isSuccessful) }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
