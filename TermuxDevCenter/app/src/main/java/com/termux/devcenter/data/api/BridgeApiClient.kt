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

class BridgeApiClient(
    private val baseUrl: String = "http://127.0.0.1:8080"
) {
    private val gson = Gson()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    suspend fun listFiles(path: String): Result<List<Map<String, Any>>> {
        return executeRequest("/files/list") {
            val json = JsonObject().apply {
                addProperty("path", path)
            }
            post(json.toString().toRequestBody("application/json".toMediaType()))
        }
    }

    suspend fun readFile(path: String): Result<String> {
        return executeRequest("/files/read") {
            val json = JsonObject().apply {
                addProperty("path", path)
            }
            post(json.toString().toRequestBody("application/json".toMediaType()))
        }
    }

    suspend fun writeFile(path: String, content: String): Result<Boolean> {
        return executeRequest("/files/write") {
            val json = JsonObject().apply {
                addProperty("path", path)
                addProperty("content", content)
            }
            post(json.toString().toRequestBody("application/json".toMediaType()))
        }
    }

    suspend fun deleteFile(path: String): Result<Boolean> {
        return executeRequest("/files/delete") {
            val json = JsonObject().apply {
                addProperty("path", path)
            }
            post(json.toString().toRequestBody("application/json".toMediaType()))
        }
    }

    suspend fun createDirectory(path: String): Result<Boolean> {
        return executeRequest("/files/mkdir") {
            val json = JsonObject().apply {
                addProperty("path", path)
            }
            post(json.toString().toRequestBody("application/json".toMediaType()))
        }
    }

    suspend fun listProjects(): Result<List<Map<String, Any>>> {
        return executeRequest("/projects/list") {
            get()
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

    // Les appels réseau sur le thread principal lèvent NetworkOnMainThreadException.
    private suspend inline fun <reified T> executeRequest(
        endpoint: String,
        crossinline requestBuilder: Request.Builder.() -> Request.Builder
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl$endpoint")
                .requestBuilder()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (response.isSuccessful && body != null) {
                val result = gson.fromJson(body, T::class.java)
                Result.success(result)
            } else {
                Result.failure(Exception("Request failed: ${response.code} - $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
