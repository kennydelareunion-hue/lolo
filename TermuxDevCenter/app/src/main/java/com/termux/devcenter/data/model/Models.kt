package com.termux.devcenter.data.model

data class ServerStatus(
    val openCodeConnected: Boolean = false,
    val serverActive: Boolean = false,
    val bridgeConnected: Boolean = false,
    val omniExecConnected: Boolean = false,
    val omniRouteConnected: Boolean = false,
    val omniRouteModelCount: Int = 0,
    val omniRouteProCount: Int = 0,
    val omniRouteFreeCount: Int = 0,
    val omniRouteMessage: String? = null,
    /** OmniRoute répond mais exige une clé API (HTTP 401/403). */
    val omniRouteAuthRequired: Boolean = false,
    val omniExecToolCount: Int = 0,
    val omniExecMessage: String? = null,
    val checked: Boolean = false
)

data class OpenCodeSession(
    val id: String,
    val projectPath: String?,
    val projectName: String?,
    val lastMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val status: SessionStatus
)

enum class SessionStatus {
    ACTIVE, IDLE, COMPLETED, ERROR
}

data class TermuxProject(
    val name: String,
    val path: String,
    val isAndroidProject: Boolean = false,
    val hasGradle: Boolean = false
)

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0,
    val permissions: String = ""
)

data class CommandResult(
    val command: String,
    val output: String,
    val error: String,
    val exitCode: Int,
    val timestamp: Long = System.currentTimeMillis()
)

data class BuildResult(
    val success: Boolean,
    val apkPath: String?,
    val logs: String,
    val duration: Long
)

data class OpenCodeMessage(
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    /** Modèle ayant produit la réponse, affiché pour savoir à qui on parle. */
    val model: ModelLabel? = null
)

data class ModelLabel(val name: String, val provider: String, val free: Boolean)

data class LogEntry(
    val level: LogLevel,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = ""
)

enum class LogLevel {
    DEBUG, INFO, WARNING, ERROR
}

data class ServerConfig(
    val openCodeAddress: String = "127.0.0.1",
    val openCodePort: Int = 0, // Dynamic
    val bridgeAddress: String = "127.0.0.1",
    val bridgePort: Int = 8080,
    val omniExecAddress: String = "127.0.0.1",
    val omniExecPort: Int = 20129
)
