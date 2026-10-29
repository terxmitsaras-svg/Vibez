package com.vibez.terminal.ui.keys

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.vibez.terminal.AppContainer
import com.vibez.terminal.data.SshKey
import com.vibez.terminal.ssh.InvalidKeyException
import com.vibez.terminal.ssh.KeyAlgorithm
import com.vibez.terminal.ssh.KeyMaterial
import com.vibez.terminal.ssh.PassphraseRequiredException
import com.vibez.terminal.ssh.SshKeys
import com.vibez.terminal.ui.components.ConfirmDialog
import com.vibez.terminal.ui.components.EmptyState
import com.vibez.terminal.ui.components.LocalSnackbar
import com.vibez.terminal.ui.components.SecretTextField
import com.vibez.terminal.ui.components.TextFieldDialog
import com.vibez.terminal.ui.theme.MonoStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A private key waiting for its passphrase before it can be imported. */
private class PendingImport(val name: String, val privateKey: ByteArray, val wrongPassphrase: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeychainScreen(container: AppContainer) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val keys by container.keys.keys.collectAsState()
    val hosts by container.hosts.hosts.collectAsState()

    var addMenu by remember { mutableStateOf(false) }
    var showGenerate by remember { mutableStateOf(false) }
    var showPaste by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }
    var renaming by remember { mutableStateOf<SshKey?>(null) }
    var deleting by remember { mutableStateOf<SshKey?>(null) }
    var exporting by remember { mutableStateOf<SshKey?>(null) }

    fun store(name: String, material: KeyMaterial, passphrase: String?) {
        container.keys.save(
            SshKey(
                name = name,
                type = material.type,
                publicKey = material.publicKey,
                fingerprint = material.fingerprint,
                encryptedPrivateKey = container.secrets.encrypt(material.privateKey),
                encryptedPassphrase = passphrase?.takeIf { it.isNotEmpty() }?.let { container.secrets.encryptString(it) },
            )
        )
    }

    fun importKey(name: String, privateKey: ByteArray, passphrase: String?) {
        scope.launch {
            val result = withContext(Dispatchers.Default) { runCatching { SshKeys.parse(privateKey, passphrase, name) } }
            result.onSuccess { material ->
                store(name, material, passphrase)
                pendingImport = null
                snackbar.showSnackbar("Imported $name")
            }.onFailure { error ->
                when {
                    error is PassphraseRequiredException -> pendingImport = PendingImport(name, privateKey)
                    error is InvalidKeyException && passphrase != null -> pendingImport = PendingImport(name, privateKey, wrongPassphrase = true)
                    else -> {
                        pendingImport = null
                        snackbar.showSnackbar(error.message ?: "Cannot import key")
                    }
                }
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { runCatching { readSmallFile(context, uri) } }
            loaded.onSuccess { (name, bytes) -> importKey(name, bytes, null) }
                .onFailure { snackbar.showSnackbar(it.message ?: "Cannot read file") }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Keychain") }) },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { addMenu = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add key") },
                )
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(text = { Text("Generate new key") }, leadingIcon = { Icon(Icons.Filled.VpnKey, null) }, onClick = {
                        addMenu = false
                        showGenerate = true
                    })
                    DropdownMenuItem(text = { Text("Import from file") }, leadingIcon = { Icon(Icons.Filled.FileOpen, null) }, onClick = {
                        addMenu = false
                        filePicker.launch(arrayOf("*/*"))
                    })
                    DropdownMenuItem(text = { Text("Paste private key") }, leadingIcon = { Icon(Icons.Filled.ContentPaste, null) }, onClick = {
                        addMenu = false
                        showPaste = true
                    })
                }
            }
        },
    ) { padding ->
        if (keys.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Key,
                title = "No keys yet",
                message = "Generate an Ed25519 key and add its public key to your servers' ~/.ssh/authorized_keys, or import an existing key.",
                modifier = Modifier.padding(padding),
            ) {
                TextButton(onClick = { showGenerate = true }) { Text("Generate key") }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
            ) {
                items(keys, key = { it.id }) { key ->
                    val usedBy = hosts.count { it.keyId == key.id }
                    KeyRow(
                        key = key,
                        usedBy = usedBy,
                        onCopyPublic = {
                            copyToClipboard(context, key.publicKey, sensitive = false)
                            scope.launch { snackbar.showSnackbar("Public key copied") }
                        },
                        onSharePublic = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, key.publicKey)
                            context.startActivity(Intent.createChooser(send, "Share public key"))
                        },
                        onExport = { exporting = key },
                        onRename = { renaming = key },
                        onDelete = { deleting = key },
                    )
                }
            }
        }
    }

    if (showGenerate) {
        GenerateKeyDialog(
            onDismiss = { showGenerate = false },
            onGenerate = { name, algorithm, passphrase, comment ->
                withContext(Dispatchers.Default) { SshKeys.generate(algorithm, comment, passphrase) }.also { material ->
                    store(name, material, passphrase)
                    snackbar.showSnackbar("Generated $name")
                }
            },
        )
    }

    if (showPaste) {
        TextFieldDialog(
            title = "Paste private key",
            label = "-----BEGIN ... PRIVATE KEY-----",
            initialValue = "",
            singleLine = false,
            confirmLabel = "Import",
            validate = { if (it.isBlank()) "Paste the whole key file" else null },
            onConfirm = { importKey("Imported key", (it.trim() + "\n").encodeToByteArray(), null) },
            onDismiss = { showPaste = false },
        )
    }

    pendingImport?.let { pending ->
        var passphrase by remember(pending) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Key passphrase") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (pending.wrongPassphrase) "Wrong passphrase, try again." else "${pending.name} is protected by a passphrase.")
                    SecretTextField(value = passphrase, onValueChange = { passphrase = it }, label = "Passphrase")
                }
            },
            confirmButton = { TextButton(onClick = { importKey(pending.name, pending.privateKey, passphrase) }) { Text("Import") } },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }

    renaming?.let { key ->
        TextFieldDialog(
            title = "Rename key",
            label = "Name",
            initialValue = key.name,
            validate = { if (it.isBlank()) "Required" else null },
            onConfirm = { container.keys.save(key.copy(name = it.trim())) },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { key ->
        val usedBy = hosts.count { it.keyId == key.id }
        ConfirmDialog(
            title = "Delete ${key.name}?",
            text = "The private key is erased from this device and cannot be recovered." +
                if (usedBy > 0) " $usedBy host(s) using it will fall back to password login." else "",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                container.keys.delete(key.id)
                container.hosts.clearKeyReferences(key.id)
            },
            onDismiss = { deleting = null },
        )
    }

    exporting?.let { key ->
        ConfirmDialog(
            title = "Copy private key?",
            text = "Anyone with your private key can log in to your servers. Only paste it somewhere you trust. " +
                if (key.encryptedPassphrase != null) "The key stays protected by its passphrase." else "This key has no passphrase.",
            confirmLabel = "Copy",
            destructive = true,
            onConfirm = {
                val pem = container.secrets.decrypt(key.encryptedPrivateKey).decodeToString()
                copyToClipboard(context, pem, sensitive = true)
                scope.launch { snackbar.showSnackbar("Private key copied") }
            },
            onDismiss = { exporting = null },
        )
    }
}

@Composable
private fun KeyRow(
    key: SshKey,
    usedBy: Int,
    onCopyPublic: () -> Unit,
    onSharePublic: () -> Unit,
    onExport: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onCopyPublic),
        leadingContent = { Icon(Icons.Filled.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(key.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(
                    buildString {
                        append(key.type)
                        if (key.encryptedPassphrase != null) append(" · passphrase")
                        if (usedBy > 0) append(" · used by $usedBy host(s)")
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(key.fingerprint, style = MonoStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Key actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Copy public key") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = {
                        menu = false
                        onCopyPublic()
                    })
                    DropdownMenuItem(text = { Text("Share public key") }, leadingIcon = { Icon(Icons.Filled.Share, null) }, onClick = {
                        menu = false
                        onSharePublic()
                    })
                    DropdownMenuItem(text = { Text("Copy private key") }, leadingIcon = { Icon(Icons.Filled.VpnKey, null) }, onClick = {
                        menu = false
                        onExport()
                    })
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = {
                        menu = false
                        onRename()
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

@Composable
private fun GenerateKeyDialog(
    onDismiss: () -> Unit,
    onGenerate: suspend (name: String, algorithm: KeyAlgorithm, passphrase: String?, comment: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("${Build.MODEL.lowercase().replace(' ', '-')}-key") }
    var algorithm by remember { mutableStateOf(KeyAlgorithm.ED25519) }
    var passphrase by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("vibez@${Build.MODEL.lowercase().replace(' ', '-')}") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("Generate key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                KeyAlgorithm.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = algorithm == option, onClick = { algorithm = option }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = algorithm == option, onClick = null)
                        Text(option.label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                SecretTextField(value = passphrase, onValueChange = { passphrase = it }, label = "Passphrase (optional)")
                OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Comment") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !working && name.isNotBlank(),
                onClick = {
                    working = true
                    scope.launch {
                        try {
                            onGenerate(name.trim(), algorithm, passphrase.ifEmpty { null }, comment.trim())
                            onDismiss()
                        } catch (e: Exception) {
                            error = e.message ?: "Key generation failed"
                        } finally {
                            working = false
                        }
                    }
                },
            ) {
                if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Generate")
            }
        },
        dismissButton = { TextButton(enabled = !working, onClick = onDismiss) { Text("Cancel") } },
    )
}

private const val MAX_KEY_FILE_BYTES = 64 * 1024

private fun readSmallFile(context: Context, uri: Uri): Pair<String, ByteArray> {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: "Imported key"
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val buffer = input.readNBytesCompat(MAX_KEY_FILE_BYTES + 1)
        require(buffer.size <= MAX_KEY_FILE_BYTES) { "That file is too large to be a key" }
        buffer
    } ?: throw IllegalArgumentException("Cannot open the file")
    return name to bytes
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

fun copyToClipboard(context: Context, text: String, sensitive: Boolean) {
    val clip = ClipData.newPlainText("Vibez", text)
    if (sensitive) {
        // ClipDescription.EXTRA_IS_SENSITIVE: hides the content from clipboard previews (Android 13+).
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    }
    context.getSystemService<ClipboardManager>()?.setPrimaryClip(clip)
}
