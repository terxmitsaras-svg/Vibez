package com.vibez.terminal

import android.content.Context
import com.vibez.terminal.data.HostRepository
import com.vibez.terminal.data.KeyRepository
import com.vibez.terminal.data.KnownHostRepository
import com.vibez.terminal.data.SecretBox
import com.vibez.terminal.data.SettingsRepository
import com.vibez.terminal.data.SnippetRepository
import com.vibez.terminal.ssh.PromptBroker
import com.vibez.terminal.ssh.SshConnector
import com.vibez.terminal.terminal.LocalShell
import com.vibez.terminal.terminal.SessionManager
import com.vibez.terminal.ui.AppLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Application-wide singletons, created once per process. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val dataDir = File(context.filesDir, "data").apply { mkdirs() }

    val secrets = SecretBox()
    val settings = SettingsRepository(context)
    val hosts = HostRepository(dataDir, appScope)
    val keys = KeyRepository(dataDir, appScope)
    val snippets = SnippetRepository(dataDir, appScope)
    val knownHosts = KnownHostRepository(dataDir, appScope)
    val prompts = PromptBroker()
    val connector = SshConnector(hosts, keys, knownHosts, secrets, prompts)
    val localShell = LocalShell(context)
    val sessions = SessionManager(context, settings, connector, prompts, localShell, appScope)
    val appLock = AppLock(settings)
}
