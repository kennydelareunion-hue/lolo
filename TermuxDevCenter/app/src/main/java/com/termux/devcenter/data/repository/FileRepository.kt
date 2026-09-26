package com.termux.devcenter.data.repository

import com.termux.devcenter.data.api.BridgeApiClient
import com.termux.devcenter.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileRepository(
    private val bridgeClient: BridgeApiClient = BridgeApiClient()
) {
    suspend fun listFiles(path: String): Result<List<FileItem>> {
        return withContext(Dispatchers.IO) {
            try {
                bridgeClient.listFiles(path).fold(
                    onSuccess = { files ->
                        val fileItems = files.map { fileMap ->
                            FileItem(
                                name = fileMap["name"] as? String ?: "",
                                path = fileMap["path"] as? String ?: "",
                                isDirectory = fileMap["isDirectory"] as? Boolean ?: false,
                                size = (fileMap["size"] as? Number)?.toLong() ?: 0L,
                                lastModified = (fileMap["lastModified"] as? Number)?.toLong() ?: 0L,
                                permissions = fileMap["permissions"] as? String ?: ""
                            )
                        }
                        Result.success(fileItems)
                    },
                    onFailure = { e ->
                        Result.failure(e)
                    }
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun readFile(path: String): Result<String> {
        return withContext(Dispatchers.IO) {
            bridgeClient.readFile(path)
        }
    }

    suspend fun writeFile(path: String, content: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            bridgeClient.writeFile(path, content)
        }
    }

    suspend fun deleteFile(path: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            bridgeClient.deleteFile(path)
        }
    }

    suspend fun createDirectory(path: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            bridgeClient.createDirectory(path)
        }
    }

    suspend fun checkConnection(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            bridgeClient.checkHealth()
        }
    }
}
