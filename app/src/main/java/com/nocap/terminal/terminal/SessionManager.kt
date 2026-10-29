package com.nocap.terminal.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.getSystemService
import com.nocap.terminal.data.AppSettings
import com.nocap.terminal.data.BellMode
import com.nocap.terminal.data.CursorStyle
import com.nocap.terminal.data.Host
import com.nocap.terminal.data.Protocol
import com.nocap.terminal.data.SettingsRepository
import com.nocap.terminal.data.newId
import com.nocap.terminal.ssh.PromptBroker
import com.nocap.terminal.ssh.SshBackend
import com.nocap.terminal.ssh.SshConnector
import com.nocap.terminal.ssh.TelnetBackend
import com.termux.terminal.TerminalBackend
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

enum class SessionKind { LOCAL, SSH }

enum class TabStatus { STARTING, RUNNING, ENDED, FAILED }

/** One terminal tab. Reconnecting replaces its [session] but keeps the tab. */
class TerminalTab internal constructor(
    val id: String,
    val kind: SessionKind,
    /** The saved or ad-hoc host of an SSH tab. */
    val host: Host?,
    private val defaultTitle: String,
    session: TerminalSession,
) {
    var session: TerminalSession = session
        internal set

    private val titleState = MutableStateFlow(defaultTitle)
    val title: StateFlow<String> = titleState.asStateFlow()

    private val statusState = MutableStateFlow(TabStatus.STARTING)
    val status: StateFlow<TabStatus> = statusState.asStateFlow()

    /** New output arrived while this tab was not shown. */
    private val activityState = MutableStateFlow(false)
    val hasActivity: StateFlow<Boolean> = activityState.asStateFlow()

    val sshBackend: SshBackend? get() = session.backend as? SshBackend

    internal fun refreshTitle() {
        titleState.value = session.title?.takeIf { it.isNotBlank() } ?: defaultTitle
    }

    internal fun setStatus(status: TabStatus) {
        statusState.value = status
    }

    internal fun setActivity(active: Boolean) {
        activityState.value = active
    }
}

/**
 * Owns all terminal sessions for the lifetime of the process, independent of any activity. The
 * [TerminalService] keeps the process alive while tabs are open. All methods must be called on the
 * main thread, which is also where [TerminalSession] delivers its callbacks.
 */
class SessionManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val connector: SshConnector,
    private val prompts: PromptBroker,
    private val localShell: LocalShell,
    scope: CoroutineScope,
) : TerminalSessionClient {

    private val tabsState = MutableStateFlow<List<TerminalTab>>(emptyList())
    val tabs: StateFlow<List<TerminalTab>> = tabsState.asStateFlow()

    private val activeTabState = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = activeTabState.asStateFlow()

    val activeTab: TerminalTab? get() = tabsState.value.firstOrNull { it.id == activeTabState.value }

    private val bellEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when a visual bell should be shown. */
    val visualBell: SharedFlow<Unit> = bellEvents.asSharedFlow()

    /** The view showing the active tab, set while the terminal screen is visible. */
    var terminalView: TerminalView? = null
        set(value) {
            field = value
            if (value != null) activeTab?.let { attach(value, it) }
        }

    /** Last known terminal geometry, so that background tabs start with a sensible size. */
    private var lastColumns = 80
    private var lastRows = 24
    private var lastCellWidth = 12
    private var lastCellHeight = 24
    private var localSessionCount = 0

    init {
        applyTheme(settings.current)
        settings.settings
            .drop(1)
            .distinctUntilChangedBy { it.terminalTheme }
            .onEach { applyTheme(it) }
            .launchIn(scope)
    }

    fun openLocal(): TerminalTab {
        val backend = localShell.createBackend(settings.current)
        localSessionCount++
        return addTab(SessionKind.LOCAL, null, "Local $localSessionCount", backend)
    }

    /** Connect to a saved or ad-hoc host with its protocol (SSH or Telnet). */
    fun openSsh(host: Host): TerminalTab = addTab(SessionKind.SSH, host, host.displayName, remoteBackend(host, tunnelOnly = false))

    /** Open only the port forwards of [host], without a shell. */
    fun openTunnel(host: Host): TerminalTab = addTab(SessionKind.SSH, host, "⇄ ${host.displayName}", remoteBackend(host, tunnelOnly = true))

    private fun remoteBackend(host: Host, tunnelOnly: Boolean): TerminalBackend = when (host.protocol) {
        Protocol.SSH -> SshBackend(host, connector, prompts, tunnelOnly)
        Protocol.TELNET -> TelnetBackend(host)
    }

    /** Open a tab on an arbitrary backend. For tests. */
    @VisibleForTesting
    internal fun openWithBackend(kind: SessionKind, host: Host?, title: String, backend: TerminalBackend): TerminalTab =
        addTab(kind, host, title, backend)

    fun select(tabId: String) {
        val tab = tabsState.value.firstOrNull { it.id == tabId } ?: return
        activeTabState.value = tabId
        tab.setActivity(false)
        terminalView?.let { attach(it, tab) }
    }

    fun selectRelative(offset: Int) {
        val list = tabsState.value
        if (list.isEmpty()) return
        val index = list.indexOfFirst { it.id == activeTabState.value }.coerceAtLeast(0)
        select(list[(index + offset).mod(list.size)].id)
    }

    /** Start the tab's session again: a new shell, or a reconnect for SSH. */
    fun restart(tabId: String) {
        val tab = tabsState.value.firstOrNull { it.id == tabId } ?: return
        tab.session.finishIfRunning()
        val backend: TerminalBackend = when (tab.kind) {
            SessionKind.LOCAL -> localShell.createBackend(settings.current)
            SessionKind.SSH -> remoteBackend(tab.host!!, tunnelOnly = (tab.session.backend as? SshBackend)?.tunnelOnly == true)
        }
        tab.session = createSession(backend)
        tab.setStatus(TabStatus.STARTING)
        tab.refreshTitle()
        if (activeTabState.value == tabId) terminalView?.let { attach(it, tab) }
        tabsState.update { it.toList() }
    }

    fun close(tabId: String) {
        val list = tabsState.value
        val index = list.indexOfFirst { it.id == tabId }
        if (index < 0) return
        val tab = list[index]
        tab.session.finishIfRunning()
        val remaining = list - tab
        tabsState.value = remaining
        if (activeTabState.value == tabId) {
            val next = remaining.getOrNull(index.coerceAtMost(remaining.lastIndex))
            activeTabState.value = next?.id
            next?.let { nextTab -> terminalView?.let { attach(it, nextTab) } }
        }
    }

    fun closeAll() {
        tabsState.value.forEach { it.session.finishIfRunning() }
        tabsState.value = emptyList()
        activeTabState.value = null
    }

    /** Type [text] into the active session, as if pasted. */
    fun sendToActive(text: String, pressEnter: Boolean) {
        val emulator = activeTab?.session?.emulator ?: return
        emulator.paste(text)
        if (pressEnter) activeTab?.session?.write("\r")
    }

    /** Re-apply settings that affect running views. */
    fun onSettingsChanged() {
        terminalView?.let { view ->
            view.mEmulator?.setCursorStyle()
            view.setTerminalCursorBlinkerRate(if (settings.current.cursorBlink) CURSOR_BLINK_RATE_MS else 0)
            view.setTerminalCursorBlinkerState(true, true)
            view.invalidate()
        }
    }

    private fun addTab(kind: SessionKind, host: Host?, title: String, backend: TerminalBackend): TerminalTab {
        val tab = TerminalTab(newId(), kind, host, title, createSession(backend))
        tabsState.update { it + tab }
        select(tab.id)
        TerminalService.start(context)
        return tab
    }

    private fun createSession(backend: TerminalBackend): TerminalSession {
        val session = TerminalSession(backend, settings.current.scrollbackLines, this)
        // Start right away so that tabs that are not visible connect too; the view resizes it later.
        session.updateSize(lastColumns, lastRows, lastCellWidth, lastCellHeight)
        applyColors(session, backend)
        return session
    }

    private fun attach(view: TerminalView, tab: TerminalTab) {
        if (view.currentSession !== tab.session) view.attachSession(tab.session)
        view.mEmulator?.let { emulator ->
            lastColumns = emulator.mColumns
            lastRows = emulator.mRows
        }
        lastCellWidth = view.mRenderer?.fontWidth?.toInt()?.coerceAtLeast(1) ?: lastCellWidth
        lastCellHeight = view.mRenderer?.fontLineSpacing?.coerceAtLeast(1) ?: lastCellHeight
        view.onScreenUpdated()
        view.invalidate()
    }

    private fun applyTheme(settings: AppSettings) {
        TerminalThemes.byName(settings.terminalTheme).applyAsDefault()
        tabsState.value.forEach { tab -> applyColors(tab.session, tab.session.backend) }
        terminalView?.invalidate()
    }

    private fun applyColors(session: TerminalSession, backend: TerminalBackend) {
        val colors = session.emulator?.mColors ?: return
        colors.reset()
        val host = when (backend) {
            is SshBackend -> backend.host
            is TelnetBackend -> backend.host
            else -> null
        }
        val override = host?.themeOverride.orEmpty()
        if (override.isNotEmpty()) TerminalThemes.byName(override).applyTo(colors)
    }

    private fun tabOf(session: TerminalSession): TerminalTab? = tabsState.value.firstOrNull { it.session === session }

    // region TerminalSessionClient

    override fun onTextChanged(changedSession: TerminalSession) {
        val tab = tabOf(changedSession) ?: return
        if (tab.status.value == TabStatus.STARTING && !changedSession.isStarting) tab.setStatus(TabStatus.RUNNING)
        if (tab.id == activeTabState.value) {
            terminalView?.onScreenUpdated()
        } else {
            tab.setActivity(true)
        }
    }

    override fun onTitleChanged(changedSession: TerminalSession) {
        val tab = tabOf(changedSession) ?: return
        if (!changedSession.isStarting && changedSession.isRunning) tab.setStatus(TabStatus.RUNNING)
        tab.refreshTitle()
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        val tab = tabOf(finishedSession) ?: return
        val failed = finishedSession.exitStatus == TerminalSession.EXIT_STATUS_START_FAILED
        tab.setStatus(if (failed) TabStatus.FAILED else TabStatus.ENDED)
        if (tab.kind == SessionKind.LOCAL && finishedSession.exitStatus == 0 && settings.current.closeLocalSessionOnExit) {
            close(tab.id)
        }
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        context.getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText("Terminal", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession) {
        val clip = context.getSystemService<ClipboardManager>()?.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(context)?.toString() ?: return
        session.emulator?.paste(text)
    }

    override fun onBell(session: TerminalSession) {
        when (settings.current.bellMode) {
            BellMode.VIBRATE -> context.getSystemService<Vibrator>()?.vibrate(
                VibrationEffect.createOneShot(BELL_VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
            )
            BellMode.VISUAL -> bellEvents.tryEmit(Unit)
            BellMode.OFF -> Unit
        }
    }

    override fun onColorsChanged(session: TerminalSession) {
        if (tabOf(session)?.id == activeTabState.value) terminalView?.invalidate()
    }

    override fun onTerminalCursorStateChange(state: Boolean) {
        terminalView?.setTerminalCursorBlinkerState(state, false)
    }

    override fun getTerminalCursorStyle(): Int = when (settings.current.cursorStyle) {
        CursorStyle.BLOCK -> 0
        CursorStyle.UNDERLINE -> 1
        CursorStyle.BAR -> 2
    }

    override fun logError(tag: String, message: String) {
        Log.e(tag, message)
    }

    override fun logWarn(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun logInfo(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun logDebug(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun logVerbose(tag: String, message: String) {
        Log.v(tag, message)
    }

    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
        Log.e(tag, message, e)
    }

    override fun logStackTrace(tag: String, e: Exception) {
        Log.e(tag, "Error", e)
    }

    // endregion

    companion object {
        const val CURSOR_BLINK_RATE_MS = 600
        private const val BELL_VIBRATION_MS = 40L
    }
}
