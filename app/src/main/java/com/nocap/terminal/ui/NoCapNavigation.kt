package com.nocap.terminal.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nocap.terminal.AppContainer
import com.nocap.terminal.terminal.TerminalController
import com.nocap.terminal.ui.components.LocalSnackbar
import com.nocap.terminal.ui.components.PromptHost
import com.nocap.terminal.ui.hosts.HostEditorScreen
import com.nocap.terminal.ui.hosts.HostsScreen
import com.nocap.terminal.ui.sessions.SessionsScreen
import com.nocap.terminal.ui.settings.KnownHostsScreen
import com.nocap.terminal.ui.settings.SettingsScreen
import com.nocap.terminal.ui.sftp.SftpHostsScreen
import com.nocap.terminal.ui.sftp.SftpScreen
import com.nocap.terminal.ui.terminal.TerminalScreen
import com.nocap.terminal.ui.vault.VAULT_TAB_SNIPPETS
import com.nocap.terminal.ui.vault.VaultScreen
import kotlinx.coroutines.flow.Flow

private object Routes {
    const val HOSTS = "hosts"
    const val SESSIONS = "sessions"
    const val SFTP_HOSTS = "sftp_hosts"
    const val VAULT = "vault?tab={tab}"
    const val SETTINGS = "settings"
    const val TERMINAL = "terminal"
    const val HOST_EDITOR = "host?id={id}"
    const val SFTP = "sftp/{hostId}"
    const val KNOWN_HOSTS = "known_hosts"

    fun hostEditor(id: String?) = if (id == null) "host" else "host?id=$id"

    fun sftp(hostId: String) = "sftp/$hostId"

    fun vault(tab: Int = 0) = "vault?tab=$tab"
}

private enum class TopLevel(val route: String, val destination: String, val label: String, val icon: ImageVector) {
    Hosts(Routes.HOSTS, Routes.HOSTS, "Hosts", Icons.Outlined.Dns),
    Sessions(Routes.SESSIONS, Routes.SESSIONS, "Terminals", Icons.Filled.Terminal),
    Sftp(Routes.SFTP_HOSTS, Routes.SFTP_HOSTS, "SFTP", Icons.Filled.FolderOpen),
    Vault(Routes.VAULT, Routes.vault(), "Vault", Icons.Filled.Key),
    Settings(Routes.SETTINGS, Routes.SETTINGS, "Settings", Icons.Filled.Settings),
}

@Composable
fun NoCapNavigation(container: AppContainer, controller: TerminalController, openTerminalRequests: Flow<Unit>) {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val tabs by container.sessions.tabs.collectAsState()

    fun openTerminal() = navController.navigate(Routes.TERMINAL) { launchSingleTop = true }

    LaunchedEffect(openTerminalRequests) {
        openTerminalRequests.collect { if (container.sessions.tabs.value.isNotEmpty()) openTerminal() }
    }
    RequestNotificationPermission(hasSessions = tabs.isNotEmpty())

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        val entry by navController.currentBackStackEntryAsState()
        val currentTopLevel = TopLevel.entries.firstOrNull { it.route == entry?.destination?.route }

        Scaffold(
            bottomBar = {
                if (currentTopLevel != null) {
                    NavigationBar {
                        TopLevel.entries.forEach { item ->
                            NavigationBarItem(
                                selected = item == currentTopLevel,
                                onClick = { navController.navigateTopLevel(item.destination) },
                                icon = {
                                    if (item == TopLevel.Sessions && tabs.isNotEmpty()) {
                                        BadgedBox(badge = { Badge { Text(tabs.size.toString()) } }) { Icon(item.icon, contentDescription = null) }
                                    } else {
                                        Icon(item.icon, contentDescription = null)
                                    }
                                },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
            contentWindowInsets = WindowInsets(0),
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOSTS,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            ) {
                composable(Routes.HOSTS) {
                    HostsScreen(
                        container = container,
                        onConnect = { host ->
                            container.sessions.openSsh(host)
                            openTerminal()
                        },
                        onEdit = { id -> navController.navigate(Routes.hostEditor(id)) },
                        onSftp = { host -> navController.navigate(Routes.sftp(host.id)) },
                        onLocalTerminal = {
                            container.sessions.openLocal()
                            openTerminal()
                        },
                        onTunnel = { host ->
                            container.sessions.openTunnel(host)
                            openTerminal()
                        },
                    )
                }
                composable(Routes.SESSIONS) { SessionsScreen(container, onOpenTerminal = ::openTerminal) }
                composable(Routes.SFTP_HOSTS) { SftpHostsScreen(container, onOpen = { host -> navController.navigate(Routes.sftp(host.id)) }) }
                composable(Routes.VAULT, arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })) { entry ->
                    VaultScreen(container, initialTab = entry.arguments?.getInt("tab") ?: 0, onRunSnippet = ::openTerminal)
                }
                composable(Routes.SETTINGS) { SettingsScreen(container, onKnownHosts = { navController.navigate(Routes.KNOWN_HOSTS) }) }
                composable(Routes.TERMINAL) {
                    TerminalScreen(
                        container = container,
                        controller = controller,
                        onBack = { navController.popIfCurrent(Routes.TERMINAL) },
                        onOpenSftp = { host -> navController.navigate(Routes.sftp(host.id)) },
                        onManageSnippets = { navController.navigateTopLevel(Routes.vault(VAULT_TAB_SNIPPETS)) },
                        onShowHosts = { navController.navigateTopLevel(Routes.HOSTS) },
                    )
                }
                composable(
                    Routes.HOST_EDITOR,
                    arguments = listOf(navArgument("id") {
                        type = NavType.StringType
                        nullable = true
                    }),
                ) { backStackEntry ->
                    HostEditorScreen(container, backStackEntry.arguments?.getString("id"), onDone = { navController.popIfCurrent(Routes.HOST_EDITOR) })
                }
                composable(Routes.SFTP) { backStackEntry ->
                    SftpScreen(container, backStackEntry.arguments?.getString("hostId").orEmpty(), onBack = { navController.popIfCurrent(Routes.SFTP) })
                }
                composable(Routes.KNOWN_HOSTS) { KnownHostsScreen(container, onBack = { navController.popIfCurrent(Routes.KNOWN_HOSTS) }) }
            }
        }
        PromptHost(container.prompts)
    }
}

private fun NavHostController.navigateTopLevel(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** Pop only if [route] is on top, so that double callbacks cannot pop an extra screen. */
private fun NavHostController.popIfCurrent(route: String) {
    if (currentDestination?.route == route) popBackStack()
}

@Composable
private fun RequestNotificationPermission(hasSessions: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(hasSessions) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (hasSessions && !granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
