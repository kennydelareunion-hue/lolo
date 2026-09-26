package com.termux.devcenter.data.repository

import com.termux.devcenter.data.api.OmniExecClient
import com.termux.devcenter.data.model.CommandResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TerminalRepository(
    private val omniExecClient: OmniExecClient = OmniExecClient()
) {
    suspend fun executeCommand(command: String, workdir: String? = null): Result<CommandResult> {
        return withContext(Dispatchers.IO) {
            try {
                val result = omniExecClient.executeCommand(command, workdir)
                
                result.fold(
                    onSuccess = { data ->
                        val resultData = (data["result"] as? Map<*, *>)
                        val content = (resultData?.get("content") as? List<*>)?.firstOrNull() as? Map<*, *>
                        val text = content?.get("text") as? String ?: ""
                        
                        // Parse the JSON result from omni-exec
                        val commandResult = CommandResult(
                            command = command,
                            output = text,
                            error = "",
                            exitCode = 0
                        )
                        Result.success(commandResult)
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

    suspend fun checkConnection(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            omniExecClient.checkHealth()
        }
    }
}
