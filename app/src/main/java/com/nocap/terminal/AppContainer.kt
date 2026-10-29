package com.nocap.terminal

import android.content.Context
import com.nocap.terminal.data.Backup
import com.nocap.terminal.data.HostRepository
import com.nocap.terminal.data.IdentityRepository
import com.nocap.terminal.data.KeyRepository
import com.nocap.terminal.data.KnownHostRepository
import com.nocap.terminal.data.SecretBox
import com.nocap.terminal.data.SettingsRepository
import com.nocap.terminal.data.SnippetRepository
import com.nocap.terminal.ssh.PromptBroker
import com.nocap.terminal.ssh.SshConnector
import com.nocap.terminal.terminal.LocalShell
import com.nocap.terminal.terminal.SessionManager
import com.nocap.terminal.ui.AppLock
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
    val identities = IdentityRepository(dataDir, appScope)
    val keys = KeyRepository(dataDir, appScope)
    val snippets = SnippetRepository(dataDir, appScope)
    val knownHosts = KnownHostRepository(dataDir, appScope)
    val prompts = PromptBroker()
    val connector = SshConnector(hosts, identities, keys, knownHosts, secrets, prompts)
    val localShell = LocalShell(context)
    val sessions = SessionManager(context, settings, connector, prompts, localShell, appScope)
    val appLock = AppLock(settings)

    /** Everything, encrypted with [password]. Blocking. */
    fun exportBackup(password: CharArray): ByteArray = Backup.encrypt(
        Backup.collect(
            hosts.hosts.value, identities.identities.value, keys.keys.value, snippets.snippets.value,
            knownHosts.knownHosts.value, settings.current, secrets,
        ),
        password,
    )

    /**
     * Merge a backup into this device: items with the same id are replaced, others are added.
     * Blocking. Returns a short summary.
     */
    fun importBackup(data: ByteArray, password: CharArray, includeSettings: Boolean): String {
        val restored = Backup.restore(Backup.decrypt(data, password), secrets)
        keys.saveAll(restored.keys)
        identities.saveAll(restored.identities)
        hosts.saveAll(restored.hosts)
        snippets.saveAll(restored.snippets)
        restored.knownHosts.forEach(knownHosts::trust)
        if (includeSettings) restored.settings?.let { imported -> settings.update { imported.copy(appLock = it.appLock) } }
        return "Imported ${restored.hosts.size} hosts, ${restored.keys.size} keys, ${restored.identities.size} identities " +
            "and ${restored.snippets.size} snippets"
    }
}
