package com.vibez.terminal.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vibez.terminal.AppContainer
import com.vibez.terminal.terminal.SessionKind
import com.vibez.terminal.terminal.TabStatus
import com.vibez.terminal.terminal.TerminalTab
import com.vibez.terminal.ui.components.EmptyState
import com.vibez.terminal.ui.theme.MonoStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(container: AppContainer, onOpenTerminal: () -> Unit) {
    val sessions = container.sessions
    val tabs by sessions.tabs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sessions") },
                actions = { if (tabs.isNotEmpty()) TextButton(onClick = sessions::closeAll) { Text("Close all") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    sessions.openLocal()
                    onOpenTerminal()
                },
                icon = { Icon(Icons.Filled.Terminal, contentDescription = null) },
                text = { Text("Local terminal") },
            )
        },
    ) { padding ->
        if (tabs.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Terminal,
                title = "No open sessions",
                message = "Sessions keep running in the background, even when you switch apps. Start a local shell or connect to a host.",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
            ) {
                items(tabs, key = { it.id }) { tab ->
                    SessionRow(
                        tab = tab,
                        onOpen = {
                            sessions.select(tab.id)
                            onOpenTerminal()
                        },
                        onClose = { sessions.close(tab.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(tab: TerminalTab, onOpen: () -> Unit, onClose: () -> Unit) {
    val title by tab.title.collectAsState()
    val status by tab.status.collectAsState()
    val statusColor = when (status) {
        TabStatus.STARTING -> Color(0xFFE3B341)
        TabStatus.RUNNING -> Color(0xFF3FB950)
        TabStatus.ENDED -> MaterialTheme.colorScheme.outline
        TabStatus.FAILED -> MaterialTheme.colorScheme.error
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        leadingContent = {
            Box {
                Icon(if (tab.kind == SessionKind.SSH) Icons.Outlined.Dns else Icons.Filled.Computer, contentDescription = null)
                Box(Modifier.size(9.dp).clip(CircleShape).background(statusColor))
            }
        },
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                when (status) {
                    TabStatus.STARTING -> if (tab.kind == SessionKind.SSH) "Connecting…" else "Starting…"
                    TabStatus.RUNNING -> tab.host?.address ?: "Local shell"
                    TabStatus.ENDED -> "Ended"
                    TabStatus.FAILED -> "Failed to start"
                },
                style = MonoStyle,
            )
        },
        trailingContent = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close session") } },
    )
}
