package com.nocap.terminal.ui.sftp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nocap.terminal.AppContainer
import com.nocap.terminal.data.Host
import com.nocap.terminal.data.Protocol
import com.nocap.terminal.ui.components.EmptyState
import com.nocap.terminal.ui.components.SectionHeader
import com.nocap.terminal.ui.hosts.HostAvatar
import com.nocap.terminal.ui.theme.MonoStyle

/** Pick a host to browse its files. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SftpHostsScreen(container: AppContainer, onOpen: (Host) -> Unit) {
    val hosts by container.hosts.hosts.collectAsState()
    val tabs by container.sessions.tabs.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }

    val connected = tabs.mapNotNull { tab -> tab.host?.takeIf { tab.sshBackend?.isConnected == true } }.distinctBy { it.id }
    val needle = query.trim().lowercase()
    val saved = hosts
        .filter { it.protocol == Protocol.SSH && connected.none { open -> open.id == it.id } }
        .filter { needle.isEmpty() || it.displayName.lowercase().contains(needle) || it.hostname.lowercase().contains(needle) }
        .sortedByDescending { it.lastConnectedAt }

    Scaffold(topBar = { TopAppBar(title = { Text("SFTP") }) }) { padding ->
        if (hosts.none { it.protocol == Protocol.SSH } && connected.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.FolderOpen,
                title = "Browse files on your servers",
                message = "Add an SSH host first. Upload, download, edit text files, rename and change permissions — over the same secure connection.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search hosts") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (connected.isNotEmpty()) {
                item { SectionHeader("Connected — opens instantly") }
                items(connected, key = { "open-" + it.id }) { host -> SftpHostRow(host, onOpen) }
            }
            if (saved.isNotEmpty()) {
                item { SectionHeader("Hosts") }
                items(saved, key = { it.id }) { host -> SftpHostRow(host, onOpen) }
            }
        }
    }
}

@Composable
private fun SftpHostRow(host: Host, onOpen: (Host) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onOpen(host) },
        leadingContent = { HostAvatar(host) },
        headlineContent = { Text(host.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(host.address, style = MonoStyle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = { Icon(Icons.Filled.FolderOpen, contentDescription = "Browse", tint = MaterialTheme.colorScheme.primary) },
    )
}
