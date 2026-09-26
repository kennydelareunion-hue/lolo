package com.termux.devcenter

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.termux.devcenter.ui.dashboard.DashboardScreen
import com.termux.devcenter.ui.opencode.OpenCodeScreen
import com.termux.devcenter.ui.projects.ProjectsScreen
import com.termux.devcenter.ui.files.FilesScreen
import com.termux.devcenter.ui.terminal.TerminalScreen
import com.termux.devcenter.ui.build.BuildScreen
import com.termux.devcenter.ui.sessions.SessionsScreen
import com.termux.devcenter.ui.settings.SettingsScreen

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Home)
    object OpenCode : Screen("opencode", "OpenCode", Icons.Default.Code)
    object Projects : Screen("projects", "Projets", Icons.Default.Folder)
    object Files : Screen("files", "Fichiers", Icons.Default.InsertDriveFile)
    object Terminal : Screen("terminal", "Terminal", Icons.Default.Terminal)
    object Build : Screen("build", "Build", Icons.Default.Build)
    object Sessions : Screen("sessions", "Sessions", Icons.Default.History)
    object Settings : Screen("settings", "Paramètres", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val screens = listOf(
        Screen.Dashboard,
        Screen.OpenCode,
        Screen.Projects,
        Screen.Files,
        Screen.Terminal,
        Screen.Build,
        Screen.Sessions,
        Screen.Settings
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Termux Dev Center") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen() }
            composable(Screen.OpenCode.route) { OpenCodeScreen() }
            composable(Screen.Projects.route) { ProjectsScreen() }
            composable(Screen.Files.route) { FilesScreen() }
            composable(Screen.Terminal.route) { TerminalScreen() }
            composable(Screen.Build.route) { BuildScreen() }
            composable(Screen.Sessions.route) { SessionsScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
