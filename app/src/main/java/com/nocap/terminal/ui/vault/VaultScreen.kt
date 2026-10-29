package com.nocap.terminal.ui.vault

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nocap.terminal.AppContainer
import com.nocap.terminal.ui.keys.KeychainScreen
import com.nocap.terminal.ui.settings.KnownHostsScreen
import com.nocap.terminal.ui.snippets.SnippetsScreen

const val VAULT_TAB_SNIPPETS = 2

/** Keys, identities, snippets and trusted host keys in one place. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(container: AppContainer, initialTab: Int = 0, onRunSnippet: () -> Unit) {
    var tab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab) }
    val tabs = listOf("Keys", "Identities", "Snippets", "Known hosts")
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Vault") })
        PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(title) },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (tab) {
            0 -> KeychainScreen(container, embedded = true)
            1 -> IdentitiesScreen(container)
            2 -> SnippetsScreen(container, onRun = onRunSnippet, embedded = true)
            else -> KnownHostsScreen(container, onBack = {}, embedded = true)
        }
    }
}
