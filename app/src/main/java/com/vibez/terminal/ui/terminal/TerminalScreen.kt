package com.vibez.terminal.ui.terminal

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.KeyboardHide
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.termux.view.TerminalView
import com.vibez.terminal.AppContainer
import com.vibez.terminal.data.Host
import com.vibez.terminal.terminal.ExtraKeysLayout
import com.vibez.terminal.terminal.SessionKind
import com.vibez.terminal.terminal.TabStatus
import com.vibez.terminal.terminal.TerminalController
import com.vibez.terminal.terminal.TerminalTab
import com.vibez.terminal.terminal.TerminalThemes
import com.vibez.terminal.ui.theme.MonoStyle
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    container: AppContainer,
    controller: TerminalController,
    onBack: () -> Unit,
    onOpenSftp: (Host) -> Unit,
    onManageSnippets: () -> Unit,
    onShowHosts: () -> Unit,
) {
    val sessions = container.sessions
    val tabs by sessions.tabs.collectAsState()
    val activeId by sessions.activeTabId.collectAsState()
    val settings by container.settings.settings.collectAsState()
    val hosts by container.hosts.hosts.collectAsState()
    val activeTab = tabs.firstOrNull { it.id == activeId }
    val theme = TerminalThemes.byName(activeTab?.host?.themeOverride?.ifEmpty { null } ?: settings.terminalTheme)
    val background = Color(theme.background)
    val foreground = Color(theme.foreground)
    val accent = Color(theme.ansi[2])
    val chrome = foreground.copy(alpha = 0.06f).compositeOver(background)
    val extraKeys = remember(settings.extraKeysLayout) { ExtraKeysLayout.parse(settings.extraKeysLayout) }

    var showSnippets by remember { mutableStateOf(false) }
    var showForwards by remember { mutableStateOf(false) }
    val bellFlash = remember { Animatable(0f) }

    LaunchedEffect(tabs.isEmpty()) {
        if (tabs.isEmpty()) onBack()
    }
    LaunchedEffect(settings.fontSizeSp, settings.keepScreenOn, settings.cursorBlink, settings.cursorStyle) {
        controller.applyAppearance(settings)
    }
    LaunchedEffect(Unit) {
        sessions.visualBell.collect {
            bellFlash.snapTo(0.25f)
            bellFlash.animateTo(0f, tween(250))
        }
    }
    LaunchedEffect(Unit) {
        delay(150)
        controller.showKeyboard()
    }
    DisposableEffect(controller) {
        controller.onShowSnippets = { showSnippets = true }
        onDispose { controller.onShowSnippets = {} }
    }
    SystemBarsAppearance(lightBars = !theme.isDark)
    BackHandler {
        if (!controller.handleBack()) onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        TabStrip(
            tabs = tabs,
            activeId = activeId,
            foreground = foreground,
            accent = accent,
            background = chrome,
            onBack = onBack,
            onSelect = sessions::select,
            onClose = sessions::close,
            actions = {
                NewSessionMenu(
                    hosts = hosts,
                    tint = foreground,
                    onLocal = { sessions.openLocal() },
                    onHost = { sessions.openSsh(it) },
                    onAllHosts = onShowHosts,
                )
                TerminalMenu(
                    container = container,
                    controller = controller,
                    tab = activeTab,
                    tint = foreground,
                    onOpenSftp = onOpenSftp,
                    onShowSnippets = { showSnippets = true },
                    onShowForwards = { showForwards = true },
                )
            },
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { context -> TerminalView(context, null).also(controller::bind) },
                onRelease = controller::unbind,
                modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
            )
            val status = activeTab?.status?.collectAsState()?.value
            if (status == TabStatus.STARTING) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter), color = accent)
            }
            if (activeTab != null && (status == TabStatus.ENDED || status == TabStatus.FAILED)) {
                SessionEndedBar(
                    tab = activeTab,
                    background = chrome,
                    foreground = foreground,
                    accent = accent,
                    onRestart = { sessions.restart(activeTab.id) },
                    onClose = { sessions.close(activeTab.id) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            if (bellFlash.value > 0f) {
                Box(Modifier.fillMaxSize().background(foreground.copy(alpha = bellFlash.value)))
            }
        }

        if (settings.showExtraKeys && extraKeys.isNotEmpty()) {
            ExtraKeysBar(extraKeys, controller, foreground, accent, chrome)
        }
    }

    if (showSnippets) {
        val snippets by container.snippets.snippets.collectAsState()
        ModalBottomSheet(onDismissRequest = { showSnippets = false }) {
            Text("Snippets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            if (snippets.isEmpty()) {
                Text(
                    "No snippets yet. Save commands you use often and run them with one tap.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            snippets.forEach { snippet ->
                ListItem(
                    headlineContent = { Text(snippet.name) },
                    supportingContent = { Text(snippet.command, style = MonoStyle, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Icon(Icons.Filled.Code, contentDescription = null) },
                    modifier = Modifier.clickable {
                        sessions.sendToActive(snippet.command, snippet.runImmediately)
                        showSnippets = false
                    },
                )
            }
            TextButton(
                onClick = {
                    showSnippets = false
                    onManageSnippets()
                },
                modifier = Modifier.padding(start = 12.dp, bottom = 24.dp),
            ) { Text("Manage snippets") }
        }
    }

    if (showForwards && activeTab != null) {
        val forwards = activeTab.sshBackend?.activeForwards.orEmpty()
        AlertDialog(
            onDismissRequest = { showForwards = false },
            title = { Text("Port forwarding") },
            text = {
                Text(
                    if (forwards.isEmpty()) "No port forwards are running on this connection. Add them in the host settings." else forwards.joinToString("\n"),
                    style = MonoStyle,
                )
            },
            confirmButton = { TextButton(onClick = { showForwards = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun TabStrip(
    tabs: List<TerminalTab>,
    activeId: String?,
    foreground: Color,
    accent: Color,
    background: Color,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    actions: @Composable () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(activeId, tabs.size) {
        val index = tabs.indexOfFirst { it.id == activeId }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = foreground) }
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(tabs, key = { it.id }) { tab ->
                TabChip(tab, tab.id == activeId, foreground, accent, onSelect = { onSelect(tab.id) }, onClose = { onClose(tab.id) })
            }
        }
        actions()
    }
}

@Composable
private fun TabChip(
    tab: TerminalTab,
    active: Boolean,
    foreground: Color,
    accent: Color,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val title by tab.title.collectAsState()
    val status by tab.status.collectAsState()
    val activity by tab.hasActivity.collectAsState()
    val statusColor = when (status) {
        TabStatus.STARTING -> Color(0xFFE3B341)
        TabStatus.RUNNING -> accent
        TabStatus.ENDED -> foreground.copy(alpha = 0.4f)
        TabStatus.FAILED -> Color(0xFFFF7B72)
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) foreground.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(start = 10.dp, end = if (active) 2.dp else 10.dp)
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(statusColor))
        Spacer(Modifier.size(6.dp))
        Icon(
            if (tab.kind == SessionKind.SSH) Icons.Outlined.Dns else Icons.Filled.Computer,
            contentDescription = null,
            tint = foreground.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.size(4.dp))
        Text(
            title,
            color = foreground.copy(alpha = if (active) 1f else 0.7f),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp),
        )
        if (activity && !active) {
            Spacer(Modifier.size(4.dp))
            Box(Modifier.size(5.dp).clip(CircleShape).background(foreground))
        }
        if (active) {
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close tab", tint = foreground.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun NewSessionMenu(
    hosts: List<Host>,
    tint: Color,
    onLocal: () -> Unit,
    onHost: (Host) -> Unit,
    onAllHosts: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Filled.Add, contentDescription = "New session", tint = tint) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Local terminal") },
                leadingIcon = { Icon(Icons.Filled.Computer, contentDescription = null) },
                onClick = {
                    expanded = false
                    onLocal()
                },
            )
            if (hosts.isNotEmpty()) HorizontalDivider()
            hosts.sortedByDescending { it.lastConnectedAt }.take(8).forEach { host ->
                DropdownMenuItem(
                    text = { Text(host.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onHost(host)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("All hosts…") },
                onClick = {
                    expanded = false
                    onAllHosts()
                },
            )
        }
    }
}

@Composable
private fun TerminalMenu(
    container: AppContainer,
    controller: TerminalController,
    tab: TerminalTab?,
    tint: Color,
    onOpenSftp: (Host) -> Unit,
    onShowSnippets: () -> Unit,
    onShowForwards: () -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val settings by container.settings.settings.collectAsState()
    fun closing(action: () -> Unit): () -> Unit = {
        expanded = false
        action()
    }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = tint) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuAction("Toggle keyboard", Icons.Filled.Keyboard, closing { controller.toggleKeyboard() })
            MenuAction("Paste", Icons.Filled.ContentPaste, closing { tab?.session?.let { container.sessions.onPasteTextFromClipboard(it) } })
            MenuAction("Snippets", Icons.Filled.Code, closing(onShowSnippets))
            val host = tab?.host
            if (tab?.kind == SessionKind.SSH && host != null) {
                MenuAction("SFTP files", Icons.Filled.Folder, closing { onOpenSftp(host) })
                MenuAction("Port forwards", Icons.Filled.SwapHoriz, closing(onShowForwards))
            }
            HorizontalDivider()
            MenuAction("Zoom in", Icons.Filled.ZoomIn, closing { controller.changeFontSize(1) })
            MenuAction("Zoom out", Icons.Filled.ZoomOut, closing { controller.changeFontSize(-1) })
            MenuAction(
                if (settings.showExtraKeys) "Hide extra keys" else "Show extra keys",
                Icons.Outlined.KeyboardHide,
                closing { container.settings.update { it.copy(showExtraKeys = !it.showExtraKeys) } },
            )
            MenuAction("Share transcript", Icons.Filled.Share, closing {
                val text = tab?.session?.emulator?.screen?.transcriptTextWithFullLinesJoined
                if (text != null) {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    context.startActivity(Intent.createChooser(send, "Share transcript"))
                }
            })
            MenuAction("Reset terminal", Icons.Filled.Refresh, closing { tab?.session?.reset() })
            if (tab != null) {
                MenuAction(
                    if (tab.kind == SessionKind.SSH) "Reconnect" else "Restart shell",
                    Icons.Outlined.RestartAlt,
                    closing { container.sessions.restart(tab.id) },
                )
                MenuAction("Close tab", Icons.Filled.Close, closing { container.sessions.close(tab.id) })
            }
        }
    }
}

@Composable
private fun MenuAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, contentDescription = null) }, onClick = onClick)
}

@Composable
private fun SessionEndedBar(
    tab: TerminalTab,
    background: Color,
    foreground: Color,
    accent: Color,
    onRestart: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (tab.kind == SessionKind.SSH) "Disconnected" else "Process ended",
            color = foreground.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClose) { Text("Close", color = foreground) }
        TextButton(onClick = onRestart) { Text(if (tab.kind == SessionKind.SSH) "Reconnect" else "Restart", color = accent) }
    }
}

/** Match the status and navigation bar icon colors to the terminal background. */
@Composable
private fun SystemBarsAppearance(lightBars: Boolean) {
    val view = LocalView.current
    DisposableEffect(lightBars) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = lightBars
        controller?.isAppearanceLightNavigationBars = lightBars
        onDispose {
            previousStatus?.let { controller.isAppearanceLightStatusBars = it }
            previousNavigation?.let { controller.isAppearanceLightNavigationBars = it }
        }
    }
}
