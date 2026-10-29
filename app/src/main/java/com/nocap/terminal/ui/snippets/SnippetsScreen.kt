package com.nocap.terminal.ui.snippets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nocap.terminal.AppContainer
import com.nocap.terminal.data.Snippet
import com.nocap.terminal.ui.components.ConfirmDialog
import com.nocap.terminal.ui.components.EmptyState
import com.nocap.terminal.ui.components.LocalSnackbar
import com.nocap.terminal.ui.theme.MonoStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetsScreen(container: AppContainer, onRun: () -> Unit, embedded: Boolean = false) {
    val snippets by container.snippets.snippets.collectAsState()
    val activeTabId by container.sessions.activeTabId.collectAsState()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Snippet?>(null) }
    var deleting by remember { mutableStateOf<Snippet?>(null) }

    val runSnippet = rememberSnippetRunner { command, pressEnter ->
        container.sessions.sendToActive(command, pressEnter)
        onRun()
    }

    fun run(snippet: Snippet) {
        if (activeTabId == null) {
            scope.launch { snackbar.showSnackbar("Open a terminal session first") }
            return
        }
        runSnippet(snippet)
    }

    Scaffold(
        topBar = { if (!embedded) TopAppBar(title = { Text("Snippets") }) },
        contentWindowInsets = if (embedded) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Snippet(name = "", command = "") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New snippet") },
            )
        },
    ) { padding ->
        if (snippets.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Code,
                title = "No snippets",
                message = "Save commands you type often and run them in any session with one tap. Use {{name}} or {{name:default}} for values to fill in when running.",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
            ) {
                items(snippets, key = { it.id }) { snippet ->
                    var menu by remember { mutableStateOf(false) }
                    ListItem(
                        modifier = Modifier.clickable { run(snippet) },
                        leadingContent = { Icon(Icons.Filled.PlayArrow, contentDescription = "Run") },
                        headlineContent = { Text(snippet.name) },
                        supportingContent = { Text(snippet.command, style = MonoStyle, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Snippet actions") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = {
                                        menu = false
                                        editing = snippet
                                    })
                                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = {
                                        menu = false
                                        deleting = snippet
                                    })
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    editing?.let { snippet ->
        SnippetDialog(snippet, onSave = container.snippets::save, onDismiss = { editing = null })
    }
    deleting?.let { snippet ->
        ConfirmDialog(
            title = "Delete ${snippet.name}?",
            text = snippet.command,
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { container.snippets.delete(snippet.id) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun SnippetDialog(initial: Snippet, onSave: (Snippet) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var command by remember { mutableStateOf(initial.command) }
    var runImmediately by remember { mutableStateOf(initial.runImmediately) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.name.isEmpty()) "New snippet" else "Edit snippet") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("Command") },
                    textStyle = MonoStyle,
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = runImmediately, onCheckedChange = { runImmediately = it })
                    Text("Press Enter after pasting")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && command.isNotBlank(), onClick = {
                onSave(initial.copy(name = name.trim(), command = command.trimEnd(), runImmediately = runImmediately))
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
