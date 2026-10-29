package com.vibez.terminal.ui.sftp

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vibez.terminal.AppContainer
import com.vibez.terminal.ssh.RemoteFile
import com.vibez.terminal.ui.components.ConfirmDialog
import com.vibez.terminal.ui.components.EmptyState
import com.vibez.terminal.ui.components.LocalSnackbar
import com.vibez.terminal.ui.components.TextFieldDialog
import com.vibez.terminal.ui.theme.MonoStyle
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SftpScreen(container: AppContainer, hostId: String, onBack: () -> Unit) {
    val viewModel: SftpViewModel = viewModel(factory = viewModelFactory { initializer { SftpViewModel(container, hostId) } })
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current

    var selected by remember { mutableStateOf<RemoteFile?>(null) }
    var creatingFolder by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<RemoteFile?>(null) }
    var deleting by remember { mutableStateOf<RemoteFile?>(null) }
    var chmodding by remember { mutableStateOf<RemoteFile?>(null) }
    var downloading by remember { mutableStateOf<RemoteFile?>(null) }

    val uploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.upload(uri, context)
    }
    val downloadPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val file = downloading
        downloading = null
        if (uri != null && file != null) viewModel.download(file, uri, context)
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    BackHandler {
        if (!viewModel.goUp()) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.hostName.ifEmpty { "SFTP" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state.path.isNotEmpty()) {
                            Text(state.path, style = MonoStyle, maxLines = 1, overflow = TextOverflow.StartEllipsis)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (state.error == null && !state.connecting) {
                        IconButton(onClick = { viewModel.goUp() }, enabled = state.path != "/") {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Parent folder")
                        }
                        IconButton(onClick = viewModel::refresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                        IconButton(onClick = { creatingFolder = true }) { Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder") }
                        IconButton(onClick = { uploadPicker.launch(arrayOf("*/*")) }) { Icon(Icons.Filled.Upload, contentDescription = "Upload") }
                    }
                },
            )
        },
        bottomBar = {
            state.transfer?.let { transfer ->
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (transfer.isUpload) "Uploading " else "Downloading ") + transfer.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::cancelTransfer) { Text("Cancel") }
                    }
                    if (transfer.total > 0) {
                        LinearProgressIndicator(
                            progress = { (transfer.transferred.toFloat() / transfer.total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${Formatter.formatShortFileSize(context, transfer.transferred)} of ${Formatter.formatShortFileSize(context, transfer.total)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.connecting -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text("Connecting…", modifier = Modifier.padding(top = 16.dp))
                }
                state.error != null -> EmptyState(
                    icon = Icons.Outlined.CloudOff,
                    title = "Cannot open SFTP",
                    message = state.error.orEmpty(),
                ) {
                    Button(onClick = viewModel::connect) { Text("Retry") }
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.files, key = { it.path }) { file ->
                        FileRow(
                            file = file,
                            onClick = { if (file.isDirectory) viewModel.open(file.path) else selected = file },
                            onLongClick = { selected = file },
                        )
                    }
                }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }

    selected?.let { file ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Text(file.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                "${file.permissions}  ${if (file.isDirectory) "" else Formatter.formatFileSize(context, file.size)}",
                style = MonoStyle,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            fun dismissThen(action: () -> Unit): () -> Unit = {
                selected = null
                action()
            }
            if (!file.isDirectory) {
                SheetAction("Download", Icons.Filled.Download, dismissThen {
                    downloading = file
                    downloadPicker.launch(file.name)
                })
            }
            SheetAction("Rename", Icons.Filled.DriveFileRenameOutline, dismissThen { renaming = file })
            SheetAction("Permissions", Icons.Filled.Lock, dismissThen { chmodding = file })
            SheetAction("Delete", Icons.Filled.Delete, dismissThen { deleting = file })
            Box(Modifier.padding(bottom = 24.dp))
        }
    }

    if (creatingFolder) {
        TextFieldDialog(
            title = "New folder",
            label = "Name",
            initialValue = "",
            confirmLabel = "Create",
            validate = ::validateName,
            onConfirm = viewModel::createDirectory,
            onDismiss = { creatingFolder = false },
        )
    }
    renaming?.let { file ->
        TextFieldDialog(
            title = "Rename",
            label = "Name",
            initialValue = file.name,
            confirmLabel = "Rename",
            validate = ::validateName,
            onConfirm = { viewModel.rename(file, it.trim()) },
            onDismiss = { renaming = null },
        )
    }
    chmodding?.let { file ->
        TextFieldDialog(
            title = "Permissions",
            label = "Octal mode, e.g. 644",
            initialValue = permissionsToOctal(file.permissions),
            confirmLabel = "Apply",
            validate = { if (Regex("[0-7]{3,4}").matches(it.trim())) null else "Use 3 or 4 octal digits" },
            onConfirm = { viewModel.changePermissions(file, it.trim()) },
            onDismiss = { chmodding = null },
        )
    }
    deleting?.let { file ->
        ConfirmDialog(
            title = "Delete ${file.name}?",
            text = if (file.isDirectory && !file.isSymlink) "The folder and everything in it is deleted on the server." else "The file is deleted on the server.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { viewModel.delete(file) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun SheetAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun FileRow(file: RemoteFile, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        leadingContent = {
            Icon(
                when {
                    file.isDirectory -> Icons.Filled.Folder
                    file.isSymlink -> Icons.Filled.Link
                    else -> Icons.AutoMirrored.Filled.InsertDriveFile
                },
                contentDescription = null,
                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
        supportingContent = {
            Text(
                buildString {
                    if (!file.isDirectory) append(Formatter.formatShortFileSize(context, file.size)).append("  ")
                    append(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(file.modifiedAt)))
                    append("  ").append(file.permissions)
                },
                style = MonoStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                maxLines = 1,
            )
        },
    )
}

private fun validateName(name: String): String? = when {
    name.isBlank() -> "Required"
    name.contains('/') -> "Names cannot contain /"
    name.trim() == "." || name.trim() == ".." -> "Not a valid name"
    else -> null
}

/** "-rwxr-x---" → "750". */
fun permissionsToOctal(permissions: String): String {
    if (permissions.length < 10) return "644"
    return (0 until 3).joinToString("") { group ->
        val bits = permissions.substring(1 + group * 3, 4 + group * 3)
        val value = (if (bits[0] == 'r') 4 else 0) + (if (bits[1] == 'w') 2 else 0) + (if (bits[2] in "xst") 1 else 0)
        value.toString()
    }
}
