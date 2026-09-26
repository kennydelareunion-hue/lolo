package com.termux.devcenter.data.repository

import com.google.gson.JsonObject
import com.termux.devcenter.TermuxDevCenterApp
import com.termux.devcenter.data.mcp.McpClient
import com.termux.devcenter.data.mcp.McpException
import com.termux.devcenter.data.mcp.McpTool
import com.termux.devcenter.data.model.CommandResult
import com.termux.devcenter.data.omniroute.OmniRouteSettings

/** Exécute les commandes du Terminal via l'outil shell du serveur MCP Omni-Exec. */
class TerminalRepository {
    private val settings = OmniRouteSettings(TermuxDevCenterApp.instance)

    suspend fun executeCommand(command: String, workdir: String? = null): Result<CommandResult> {
        val client = McpClient.forUrl(settings.current().mcpUrl)
        val tool = client.listTools().getOrElse { return Result.failure(it) }.let(::pickShellTool)
            ?: return Result.failure(McpException("Omni-Exec n'expose aucun outil d'exécution de commande."))

        val properties = tool.inputSchema.getAsJsonObject("properties")?.keySet().orEmpty()
        val commandKey = listOf("command", "cmd", "script", "input").firstOrNull { it in properties }
            ?: properties.firstOrNull() ?: "command"
        val args = JsonObject().apply {
            addProperty(commandKey, command)
            if (workdir != null) {
                listOf("workdir", "cwd", "directory").firstOrNull { it in properties }
                    ?.let { addProperty(it, workdir) }
            }
        }
        return client.callTool(tool.name, args).map {
            CommandResult(
                command = command,
                output = if (it.isError) "" else it.text,
                error = if (it.isError) it.text else "",
                exitCode = if (it.isError) 1 else 0
            )
        }
    }

    private fun pickShellTool(tools: List<McpTool>): McpTool? {
        val preferred = listOf("run_command", "execute_command", "exec", "run_long_command", "shell", "bash")
        return preferred.firstNotNullOfOrNull { name -> tools.firstOrNull { it.name == name } }
            ?: tools.firstOrNull { t -> listOf("command", "exec", "shell", "bash", "run").any { t.name.contains(it, true) } }
    }
}
