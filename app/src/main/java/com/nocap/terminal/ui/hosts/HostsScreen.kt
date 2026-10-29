package com.nocap.terminal.ui.hosts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nocap.terminal.AppContainer
import com.nocap.terminal.R
import com.nocap.terminal.data.Host
import com.nocap.terminal.data.Protocol
import com.nocap.terminal.data.SshConfigImporter
import com.nocap.terminal.data.newId
import com.nocap.terminal.ssh.QuickConnect
import com.nocap.terminal.ui.components.ConfirmDialog
import com.nocap.terminal.ui.components.EmptyState
import com.nocap.terminal.ui.components.LocalSnackbar
import com.nocap.terminal.ui.components.SectionHeader
import com.nocap.terminal.ui.theme.MonoStyle
import com.nocap.terminal.ui.theme.hostColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostsScreen(
    container: AppContainer,
    onConnect: (Host) -> Unit,
    onEdit: (hostId: String?) -> Unit,
    onSftp: (Host) -> Unit,
    onLocalTerminal: () -> Unit,
    onTunnel: (Host) -> Unit = {},
) {
    val hosts by container.hosts.hosts.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var quickConnect by rememberSaveable { mutableStateOf("") }
    var quickConnectError by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Host?>(null) }

    val groups = remember(hosts, query) {
        val needle = query.trim().lowercase()
        hosts
            .filter { host ->
                needle.isEmpty() || listOf(host.label, host.hostname, host.username, host.group, *host.tags.toTypedArray())
                    .any { it.lowercase().contains(needle) }
            }
            .sortedBy { it.displayName.lowercase() }
            .groupBy { it.group.trim() }
            .toSortedMap(compareBy<String>({ it.isNotEmpty() }, { it.lowercase() }))
    }

    fun submitQuickConnect() {
        val host = QuickConnect.parse(quickConnect)
        quickConnectError = host == null
        if (host != null) {
            quickConnect = ""
            onConnect(host)
        }
    }

    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val configPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                        ?: throw IllegalArgumentException("Cannot read the file")
                    SshConfigImporter.parse(text, container.hosts.hosts.value)
                }
            }
            result.onSuccess { imported ->
                container.hosts.saveAll(imported.hosts)
                val skipped = if (imported.skipped.isEmpty()) "" else " (${imported.skipped.size} entries skipped)"
                snackbar.showSnackbar("Imported ${imported.hosts.size} hosts$skipped")
            }.onFailure { snackbar.showSnackbar(it.message ?: "Import failed") }
        }
    }
    var menu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { BrandTitle() },
                actions = {
                    IconButton(onClick = onLocalTerminal) { Icon(Icons.Filled.Terminal, contentDescription = "Local terminal") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Import ~/.ssh/config") },
                                leadingIcon = { Icon(Icons.Filled.FileOpen, null) },
                                onClick = {
                                    menu = false
                                    configPicker.launch(arrayOf("*/*"))
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(null) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add host") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
        ) {
            item {
                OutlinedTextField(
                    value = quickConnect,
                    onValueChange = {
                        quickConnect = it
                        quickConnectError = false
                    },
                    placeholder = { Text("user@host:port") },
                    label = { Text("Quick connect") },
                    singleLine = true,
                    isError = quickConnectError,
                    supportingText = if (quickConnectError) {
                        { Text("Use user@host, user@host:port or ssh user@host -p port") }
                    } else {
                        null
                    },
                    textStyle = MonoStyle,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onGo = { submitQuickConnect() }),
                    trailingIcon = {
                        IconButton(onClick = ::submitQuickConnect, enabled = quickConnect.isNotBlank()) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Connect")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (hosts.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search hosts, tags, groups") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            if (hosts.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Dns,
                        title = "No saved hosts",
                        message = "Save the servers you connect to, with keys, jump hosts and port forwards. Or open a terminal on this device.",
                        modifier = Modifier.padding(top = 24.dp),
                    ) {
                        OutlinedButton(onClick = onLocalTerminal) { Text("Local terminal") }
                    }
                }
            }
            groups.forEach { (group, groupHosts) ->
                if (groups.size > 1 || group.isNotEmpty()) {
                    item(key = "group:$group") { SectionHeader(group.ifEmpty { "Ungrouped" }) }
                }
                items(groupHosts, key = { it.id }) { host ->
                    HostRow(
                        host = host,
                        onClick = { onConnect(host) },
                        onSftp = { onSftp(host) },
                        onTunnel = { onTunnel(host) },
                        onEdit = { onEdit(host.id) },
                        onDuplicate = {
                            container.hosts.save(host.copy(id = newId(), label = "${host.displayName} (copy)", lastConnectedAt = 0))
                        },
                        onDelete = { pendingDelete = host },
                    )
                }
            }
        }
    }

    pendingDelete?.let { host ->
        ConfirmDialog(
            title = "Delete ${host.displayName}?",
            text = "The host and its saved password are removed. Keys in the keychain are kept.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { container.hosts.delete(host.id) },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun HostRow(
    host: Host,
    onClick: () -> Unit,
    onSftp: () -> Unit,
    onTunnel: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { HostAvatar(host) },
        headlineContent = { Text(host.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val details = buildList {
                if (host.protocol == Protocol.TELNET) add("telnet")
                if (host.label.isNotBlank() || host.port != host.protocol.defaultPort) add(host.address)
                if (host.identityId != null) add("identity")
                if (host.jumpHostId != null) add("via jump host")
                if (host.portForwards.isNotEmpty()) add("${host.portForwards.size} forward(s)")
                addAll(host.tags.map { "#$it" })
            }
            if (details.isNotEmpty()) {
                Text(details.joinToString("  ·  "), style = MonoStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Host actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (host.protocol == Protocol.SSH) {
                        DropdownMenuItem(text = { Text("SFTP files") }, leadingIcon = { Icon(Icons.Filled.Folder, null) }, onClick = {
                            menu = false
                            onSftp()
                        })
                    }
                    if (host.protocol == Protocol.SSH && host.portForwards.any { it.enabled }) {
                        DropdownMenuItem(text = { Text("Start tunnels only") }, leadingIcon = { Icon(Icons.Filled.SwapHoriz, null) }, onClick = {
                            menu = false
                            onTunnel()
                        })
                    }
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = {
                        menu = false
                        onEdit()
                    })
                    DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = {
                        menu = false
                        onDuplicate()
                    })
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = {
                        menu = false
                        onDelete()
                    })
                }
            }
        },
    )
}

/** The app logo, a Caps Lock key that only says "Cap", next to the app name. */
@Composable
fun BrandTitle() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
        )
        Text("No Cap Terminal", modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
fun HostAvatar(host: Host, modifier: Modifier = Modifier) {
    val color = hostColor(host.color)
    val initials = host.displayName.filter { it.isLetterOrDigit() }.take(2).uppercase().ifEmpty { "?" }
    Box(
        modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color?.copy(alpha = 0.25f) ?: MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            color = color ?: MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

/** A row of selectable color dots. */
@Composable
fun ColorPicker(selected: String, options: List<String>, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        options.forEach { name ->
            val color = hostColor(name)
            Box(
                Modifier
                    .size(if (name == selected) 34.dp else 28.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color ?: MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(name) },
                contentAlignment = Alignment.Center,
            ) {
                if (name == selected) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(if (color == null) MaterialTheme.colorScheme.onSurface else Color.White))
                }
            }
        }
    }
}
