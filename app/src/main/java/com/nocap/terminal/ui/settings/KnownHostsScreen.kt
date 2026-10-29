package com.nocap.terminal.ui.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nocap.terminal.AppContainer
import com.nocap.terminal.data.KnownHost
import com.nocap.terminal.ui.components.ConfirmDialog
import com.nocap.terminal.ui.components.EmptyState
import com.nocap.terminal.ui.theme.MonoStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnownHostsScreen(container: AppContainer, onBack: () -> Unit, embedded: Boolean = false) {
    val knownHosts by container.knownHosts.knownHosts.collectAsState()
    var removing by remember { mutableStateOf<KnownHost?>(null) }

    Scaffold(
        contentWindowInsets = if (embedded) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (!embedded) TopAppBar(
                title = { Text("Known hosts") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        if (knownHosts.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.VerifiedUser,
                title = "No trusted hosts",
                message = "When you connect to a server for the first time, you are asked to verify and trust its host key. Trusted keys appear here.",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                items(knownHosts.sortedBy { it.host }, key = { it.host + it.keyType }) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.host, style = MonoStyle) },
                        supportingContent = {
                            Text(
                                "${entry.keyType}\n${entry.fingerprint}",
                                style = MonoStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { removing = entry }) { Icon(Icons.Filled.Delete, contentDescription = "Forget") }
                        },
                    )
                }
            }
        }
    }

    removing?.let { entry ->
        ConfirmDialog(
            title = "Forget ${entry.host}?",
            text = "You will be asked to verify the host key again on the next connection.",
            confirmLabel = "Forget",
            destructive = true,
            onConfirm = { container.knownHosts.remove(entry.host, entry.keyType) },
            onDismiss = { removing = null },
        )
    }
}
