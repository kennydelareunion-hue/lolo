package com.termux.devcenter.data.termux

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Lance une commande dans Termux via l'API RUN_COMMAND.
 * Nécessite `allow-external-apps = true` dans ~/.termux/termux.properties.
 */
object TermuxLauncher {
    const val PERMISSION = "com.termux.permission.RUN_COMMAND"
    private const val TERMUX_PACKAGE = "com.termux"
    private const val BASH = "/data/data/com.termux/files/usr/bin/bash"

    fun isTermuxInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
    }.isSuccess

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun runInBackground(context: Context, command: String): Result<Unit> = runCatching {
        val intent = Intent().apply {
            setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
            action = "com.termux.RUN_COMMAND"
            putExtra("com.termux.RUN_COMMAND_PATH", BASH)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", command))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        context.startService(intent) ?: error("Service Termux introuvable")
    }

    /**
     * Démarre OmniRoute détaché du shell pour qu'il survive à la fin de la commande.
     * Pas de test « déjà lancé » : une seconde instance échoue simplement à ouvrir le port.
     */
    fun startOmniRoute(context: Context, startCommand: String): Result<Unit> =
        runInBackground(
            context,
            "mkdir -p ~/.omniroute; nohup $startCommand > ~/.omniroute/omniroute.log 2>&1 &"
        )
}
