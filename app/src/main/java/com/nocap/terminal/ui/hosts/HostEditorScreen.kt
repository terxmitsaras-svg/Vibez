package com.nocap.terminal.ui.hosts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nocap.terminal.AppContainer
import com.nocap.terminal.data.ForwardType
import com.nocap.terminal.data.Host
import com.nocap.terminal.data.HostColors
import com.nocap.terminal.data.PortForward
import com.nocap.terminal.data.Protocol
import com.nocap.terminal.data.ProxyConfig
import com.nocap.terminal.data.ProxyType
import com.nocap.terminal.terminal.TerminalThemes
import com.nocap.terminal.ui.components.SecretTextField
import com.nocap.terminal.ui.components.SectionHeader
import com.nocap.terminal.ui.theme.MonoStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostEditorScreen(container: AppContainer, hostId: String?, onDone: () -> Unit) {
    val existing = remember(hostId) { container.hosts.get(hostId) }
    val keys by container.keys.keys.collectAsState()
    val allHosts by container.hosts.hosts.collectAsState()

    var label by rememberSaveable { mutableStateOf(existing?.label.orEmpty()) }
    var hostname by rememberSaveable { mutableStateOf(existing?.hostname.orEmpty()) }
    var port by rememberSaveable { mutableStateOf((existing?.port ?: 22).toString()) }
    var username by rememberSaveable { mutableStateOf(existing?.username.orEmpty()) }
    var password by rememberSaveable { mutableStateOf("") }
    var removePassword by rememberSaveable { mutableStateOf(false) }
    var keyId by rememberSaveable { mutableStateOf(existing?.keyId) }
    var jumpHostId by rememberSaveable { mutableStateOf(existing?.jumpHostId) }
    var group by rememberSaveable { mutableStateOf(existing?.group.orEmpty()) }
    var tags by rememberSaveable { mutableStateOf(existing?.tags.orEmpty().joinToString(", ")) }
    var color by rememberSaveable { mutableStateOf(existing?.color ?: HostColors.NONE) }
    var startupCommand by rememberSaveable { mutableStateOf(existing?.startupCommand.orEmpty()) }
    var keepAlive by rememberSaveable { mutableStateOf((existing?.keepAliveSeconds ?: 30).toString()) }
    var compression by rememberSaveable { mutableStateOf(existing?.compression ?: false) }
    var terminalType by rememberSaveable { mutableStateOf(existing?.terminalType ?: "xterm-256color") }
    var themeOverride by rememberSaveable { mutableStateOf(existing?.themeOverride.orEmpty()) }
    val forwards = remember { mutableStateListOf<PortForward>().apply { addAll(existing?.portForwards.orEmpty()) } }
    var editingForward by remember { mutableStateOf<PortForward?>(null) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    val identities by container.identities.identities.collectAsState()
    var protocol by rememberSaveable { mutableStateOf(existing?.protocol ?: Protocol.SSH) }
    var identityId by rememberSaveable { mutableStateOf(existing?.identityId) }
    var agentForwarding by rememberSaveable { mutableStateOf(existing?.agentForwarding ?: false) }
    var environment by rememberSaveable {
        mutableStateOf(existing?.environment.orEmpty().entries.joinToString("\n") { "${it.key}=${it.value}" })
    }
    var proxyType by rememberSaveable { mutableStateOf(existing?.proxy?.type) }
    var proxyHost by rememberSaveable { mutableStateOf(existing?.proxy?.hostname.orEmpty()) }
    var proxyPort by rememberSaveable { mutableStateOf(existing?.proxy?.port?.toString() ?: "1080") }
    var proxyUser by rememberSaveable { mutableStateOf(existing?.proxy?.username.orEmpty()) }
    var proxyPassword by rememberSaveable { mutableStateOf("") }
    val isSsh = protocol == Protocol.SSH

    val portNumber = port.toIntOrNull()
    val hostnameError = if (hostname.isBlank()) "Required" else if (hostname.any { it.isWhitespace() }) "No spaces allowed" else null
    val usernameError = if (username.isBlank() && isSsh && identityId == null) "Required" else null
    val environmentError = environment.lines().map { it.trim() }.filter { it.isNotEmpty() }
        .firstOrNull { !Regex("[A-Za-z_][A-Za-z0-9_]*=.*").matches(it) }?.let { "Use NAME=value: $it" }
    val proxyError = if (proxyType != null && (proxyHost.isBlank() || proxyPort.toIntOrNull()?.let { it in 1..65535 } != true)) "Proxy host and port required" else null
    val portError = if (portNumber == null || portNumber !in 1..65535) "1–65535" else null
    val keepAliveError = if (keepAlive.toIntOrNull()?.let { it in 0..3600 } != true) "0–3600 seconds" else null
    val valid = hostnameError == null && usernameError == null && portError == null && keepAliveError == null &&
        environmentError == null && proxyError == null

    fun save() {
        showErrors = true
        if (!valid) return
        val base = existing ?: Host(hostname = "", username = "")
        val encryptedPassword = when {
            password.isNotEmpty() -> container.secrets.encryptString(password)
            removePassword -> null
            else -> base.encryptedPassword
        }
        container.hosts.save(
            base.copy(
                label = label.trim(),
                hostname = hostname.trim(),
                port = portNumber!!,
                username = username.trim(),
                encryptedPassword = encryptedPassword,
                keyId = keyId,
                jumpHostId = jumpHostId,
                group = group.trim(),
                tags = tags.split(',').map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }.distinct(),
                color = color,
                startupCommand = startupCommand.trim(),
                keepAliveSeconds = keepAlive.toInt(),
                compression = compression,
                terminalType = terminalType.trim().ifEmpty { "xterm-256color" },
                themeOverride = themeOverride,
                portForwards = forwards.toList(),
                protocol = protocol,
                identityId = identityId,
                agentForwarding = agentForwarding,
                environment = environment.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    .associate { it.substringBefore('=') to it.substringAfter('=') },
                proxy = proxyType?.let { type ->
                    ProxyConfig(
                        type = type,
                        hostname = proxyHost.trim(),
                        port = proxyPort.toInt(),
                        username = proxyUser.trim(),
                        encryptedPassword = if (proxyPassword.isNotEmpty()) {
                            container.secrets.encryptString(proxyPassword)
                        } else {
                            base.proxy?.encryptedPassword?.takeIf { proxyUser.isNotBlank() }
                        },
                    )
                },
            )
        )
        onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New host" else "Edit host") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = { TextButton(onClick = ::save) { Text("Save") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader("Connection", horizontalPadding = 0.dp)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Protocol.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = protocol == option,
                        onClick = {
                            if (port.toIntOrNull() == protocol.defaultPort) port = option.defaultPort.toString()
                            protocol = option
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, Protocol.entries.size),
                    ) { Text(if (option == Protocol.SSH) "SSH" else "Telnet") }
                }
            }
            if (!isSsh) {
                Text(
                    "Telnet is not encrypted. Use it only for local devices like routers and switches.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                value = hostname,
                onValueChange = { hostname = it },
                label = { Text("Hostname or IP") },
                singleLine = true,
                isError = showErrors && hostnameError != null,
                supportingText = if (showErrors && hostnameError != null) ({ Text(hostnameError) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    isError = showErrors && usernameError != null,
                    supportingText = if (showErrors && usernameError != null) ({ Text(usernameError) }) else null,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    label = { Text("Port") },
                    singleLine = true,
                    isError = portError != null,
                    supportingText = portError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (isSsh) SectionHeader("Authentication", horizontalPadding = 0.dp)
            if (isSsh) Dropdown(
                label = "Identity",
                selected = identities.firstOrNull { it.id == identityId }?.let { "${it.name} (${it.username})" } ?: "None — use the fields below",
                options = listOf<Pair<String?, String>>(null to "None — use the fields below") + identities.map { it.id to "${it.name} (${it.username})" },
                onSelect = { identityId = it },
            )
            if (isSsh && identityId == null) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SecretTextField(
                value = password,
                onValueChange = {
                    password = it
                    removePassword = false
                },
                label = if (existing?.encryptedPassword != null && !removePassword) "New password (saved one is kept)" else "Password (optional)",
                supportingText = "Leave empty to be asked when connecting. Stored encrypted with the Android Keystore.",
            )
            if (existing?.encryptedPassword != null && !removePassword && password.isEmpty()) {
                TextButton(onClick = { removePassword = true }) { Text("Remove saved password") }
            }
            Dropdown(
                label = "SSH key",
                selected = keys.firstOrNull { it.id == keyId }?.let { "${it.name} (${it.type})" } ?: "None",
                options = listOf<Pair<String?, String>>(null to "None") + keys.map { it.id to "${it.name} (${it.type})" },
                onSelect = { keyId = it },
            )

            }

            SectionHeader("Organize", horizontalPadding = 0.dp)
            OutlinedTextField(
                value = group,
                onValueChange = { group = it },
                label = { Text("Group") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Tags, comma separated") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Color", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ColorPicker(selected = color, options = HostColors.all, onSelect = { color = it })

            SectionHeader("Advanced", horizontalPadding = 0.dp)
            if (isSsh) Dropdown(
                label = "Jump host (ProxyJump)",
                selected = allHosts.firstOrNull { it.id == jumpHostId }?.displayName ?: "None",
                options = listOf<Pair<String?, String>>(null to "None") +
                    allHosts.filter { it.id != existing?.id }.map { it.id to it.displayName },
                onSelect = { jumpHostId = it },
            )
            OutlinedTextField(
                value = startupCommand,
                onValueChange = { startupCommand = it },
                label = { Text("Startup command") },
                placeholder = { Text("e.g. tmux new -A -s main") },
                singleLine = true,
                textStyle = MonoStyle,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = keepAlive,
                    onValueChange = { keepAlive = it.filter(Char::isDigit).take(4) },
                    label = { Text("Keep-alive (s)") },
                    singleLine = true,
                    isError = keepAliveError != null,
                    supportingText = keepAliveError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = terminalType,
                    onValueChange = { terminalType = it },
                    label = { Text("TERM") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.weight(1f),
                )
            }
            if (isSsh) Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Compression", style = MaterialTheme.typography.bodyLarge)
                    Text("Helps on slow links", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = compression, onCheckedChange = { compression = it })
            }
            if (isSsh) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Agent forwarding", style = MaterialTheme.typography.bodyLarge)
                        Text("Use this host's key from the server, e.g. for git (ssh -A)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = agentForwarding, onCheckedChange = { agentForwarding = it })
                }
                OutlinedTextField(
                    value = environment,
                    onValueChange = { environment = it },
                    label = { Text("Environment variables") },
                    placeholder = { Text("LANG=en_US.UTF-8") },
                    isError = environmentError != null,
                    supportingText = { Text(environmentError ?: "One NAME=value per line. The server must allow them (AcceptEnv).") },
                    textStyle = MonoStyle,
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                Dropdown(
                    label = "Proxy",
                    selected = proxyType?.name ?: "None",
                    options = listOf<Pair<String?, String>>(null to "None") + ProxyType.entries.map { it.name to it.name },
                    onSelect = { name -> proxyType = name?.let(ProxyType::valueOf) },
                )
                if (proxyType != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = proxyHost,
                            onValueChange = { proxyHost = it },
                            label = { Text("Proxy host") },
                            singleLine = true,
                            isError = proxyError != null,
                            modifier = Modifier.weight(2f),
                        )
                        OutlinedTextField(
                            value = proxyPort,
                            onValueChange = { proxyPort = it.filter(Char::isDigit).take(5) },
                            label = { Text("Port") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = proxyUser,
                        onValueChange = { proxyUser = it },
                        label = { Text("Proxy username (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (proxyUser.isNotBlank()) {
                        SecretTextField(
                            value = proxyPassword,
                            onValueChange = { proxyPassword = it },
                            label = if (existing?.proxy?.encryptedPassword != null) "Proxy password (saved one is kept)" else "Proxy password",
                        )
                    }
                }
            }
            Dropdown(
                label = "Terminal theme",
                selected = themeOverride.ifEmpty { "App default" },
                options = listOf<Pair<String?, String>>("" to "App default") + TerminalThemes.all.map { it.name to it.name },
                onSelect = { themeOverride = it.orEmpty() },
            )

            if (isSsh) {
                SectionHeader("Port forwarding", horizontalPadding = 0.dp)
                forwards.forEach { forward ->
                    ListItem(
                        headlineContent = { Text(forward.describe(), style = MonoStyle) },
                        trailingContent = {
                            Row {
                                Switch(checked = forward.enabled, onCheckedChange = { enabled ->
                                    forwards[forwards.indexOf(forward)] = forward.copy(enabled = enabled)
                                })
                                IconButton(onClick = { forwards.remove(forward) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = { editingForward = PortForward(bindPort = 8080, destinationHost = "localhost", destinationPort = 80) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add port forward")
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    editingForward?.let { initial ->
        PortForwardDialog(
            initial = initial,
            onDismiss = { editingForward = null },
            onSave = { forwards += it },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dropdown(label: String, selected: String, options: List<Pair<String?, String>>, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = {
                    onSelect(value)
                    expanded = false
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortForwardDialog(initial: PortForward, onDismiss: () -> Unit, onSave: (PortForward) -> Unit) {
    var type by remember { mutableStateOf(initial.type) }
    var bindAddress by remember { mutableStateOf(initial.bindAddress) }
    var bindPort by remember { mutableStateOf(initial.bindPort.toString()) }
    var destinationHost by remember { mutableStateOf(initial.destinationHost) }
    var destinationPort by remember { mutableStateOf(initial.destinationPort.toString()) }

    val bindPortValid = bindPort.toIntOrNull()?.let { it in 1..65535 } == true
    val destinationValid = type == ForwardType.DYNAMIC ||
        (destinationHost.isNotBlank() && destinationPort.toIntOrNull()?.let { it in 1..65535 } == true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Port forward") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ForwardType.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = type == option,
                            onClick = { type = option },
                            shape = SegmentedButtonDefaults.itemShape(index, ForwardType.entries.size),
                        ) { Text(option.name.lowercase().replaceFirstChar(Char::uppercase)) }
                    }
                }
                Text(
                    when (type) {
                        ForwardType.LOCAL -> "Connections to a port on this device are forwarded to a destination reachable from the server (-L)."
                        ForwardType.REMOTE -> "Connections to a port on the server are forwarded to a destination reachable from this device (-R)."
                        ForwardType.DYNAMIC -> "A SOCKS5 proxy on this device; apps using it connect through the server (-D)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = bindAddress,
                        onValueChange = { bindAddress = it },
                        label = { Text(if (type == ForwardType.REMOTE) "Server address" else "Listen address") },
                        singleLine = true,
                        modifier = Modifier.weight(3f),
                    )
                    OutlinedTextField(
                        value = bindPort,
                        onValueChange = { bindPort = it.filter(Char::isDigit).take(5) },
                        label = { Text("Port") },
                        singleLine = true,
                        isError = !bindPortValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(2f),
                    )
                }
                if (type != ForwardType.DYNAMIC) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = destinationHost,
                            onValueChange = { destinationHost = it },
                            label = { Text("Destination") },
                            singleLine = true,
                            modifier = Modifier.weight(3f),
                        )
                        OutlinedTextField(
                            value = destinationPort,
                            onValueChange = { destinationPort = it.filter(Char::isDigit).take(5) },
                            label = { Text("Port") },
                            singleLine = true,
                            isError = !destinationValid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(2f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = bindPortValid && destinationValid && bindAddress.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            type = type,
                            bindAddress = bindAddress.trim(),
                            bindPort = bindPort.toInt(),
                            destinationHost = if (type == ForwardType.DYNAMIC) "" else destinationHost.trim(),
                            destinationPort = if (type == ForwardType.DYNAMIC) 0 else destinationPort.toInt(),
                        )
                    )
                    onDismiss()
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
