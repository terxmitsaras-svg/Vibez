package com.nocap.terminal.ui.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nocap.terminal.AppContainer
import com.nocap.terminal.data.Identity
import com.nocap.terminal.ui.components.ConfirmDialog
import com.nocap.terminal.ui.components.EmptyState
import com.nocap.terminal.ui.components.SecretTextField

/** Reusable credentials (username + password and/or key) that hosts can share. */
@Composable
fun IdentitiesScreen(container: AppContainer) {
    val identities by container.identities.identities.collectAsState()
    val hosts by container.hosts.hosts.collectAsState()
    val keys by container.keys.keys.collectAsState()
    var editing by remember { mutableStateOf<Identity?>(null) }
    var deleting by remember { mutableStateOf<Identity?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Identity(name = "", username = "") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New identity") },
            )
        },
    ) { padding ->
        if (identities.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Badge,
                title = "No identities",
                message = "An identity is a username with a password and/or key. Give it to many hosts and change the password in one place.",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 88.dp)) {
                items(identities, key = { it.id }) { identity ->
                    val usedBy = hosts.count { it.identityId == identity.id }
                    ListItem(
                        modifier = Modifier.clickable { editing = identity },
                        leadingContent = { Icon(Icons.Filled.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(identity.name) },
                        supportingContent = {
                            Text(
                                buildList {
                                    add(identity.username)
                                    if (identity.encryptedPassword != null) add("password")
                                    keys.firstOrNull { it.id == identity.keyId }?.let { add("key ${it.name}") }
                                    if (usedBy > 0) add("$usedBy host(s)")
                                }.joinToString(" · "),
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { deleting = identity }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                        },
                    )
                }
            }
        }
    }

    editing?.let { identity ->
        IdentityDialog(container, identity, onDismiss = { editing = null })
    }
    deleting?.let { identity ->
        ConfirmDialog(
            title = "Delete ${identity.name}?",
            text = "Hosts using it keep their own username and fall back to asking for a password.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                container.identities.delete(identity.id)
                container.hosts.clearIdentityReferences(identity.id)
            },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdentityDialog(container: AppContainer, initial: Identity, onDismiss: () -> Unit) {
    val keys by container.keys.keys.collectAsState()
    var name by remember { mutableStateOf(initial.name) }
    var username by remember { mutableStateOf(initial.username) }
    var password by remember { mutableStateOf("") }
    var removePassword by remember { mutableStateOf(false) }
    var keyId by remember { mutableStateOf(initial.keyId) }
    var keyMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.name.isEmpty()) "New identity" else "Edit identity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                SecretTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        removePassword = false
                    },
                    label = if (initial.encryptedPassword != null && !removePassword) "New password (saved one is kept)" else "Password (optional)",
                )
                if (initial.encryptedPassword != null && !removePassword && password.isEmpty()) {
                    TextButton(onClick = { removePassword = true }) { Text("Remove saved password") }
                }
                ExposedDropdownMenuBox(expanded = keyMenu, onExpandedChange = { keyMenu = it }) {
                    OutlinedTextField(
                        value = keys.firstOrNull { it.id == keyId }?.name ?: "None",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("SSH key") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(keyMenu) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = keyMenu, onDismissRequest = { keyMenu = false }) {
                        DropdownMenuItem(text = { Text("None") }, onClick = {
                            keyId = null
                            keyMenu = false
                        })
                        keys.forEach { key ->
                            DropdownMenuItem(text = { Text("${key.name} (${key.type})") }, onClick = {
                                keyId = key.id
                                keyMenu = false
                            })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && username.isNotBlank(), onClick = {
                val encryptedPassword = when {
                    password.isNotEmpty() -> container.secrets.encryptString(password)
                    removePassword -> null
                    else -> initial.encryptedPassword
                }
                container.identities.save(
                    initial.copy(name = name.trim(), username = username.trim(), encryptedPassword = encryptedPassword, keyId = keyId)
                )
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
