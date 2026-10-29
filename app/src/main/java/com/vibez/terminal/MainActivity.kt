package com.vibez.terminal

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import com.vibez.terminal.terminal.TerminalController
import com.vibez.terminal.ui.LockScreen
import com.vibez.terminal.ui.VibezNavigation
import com.vibez.terminal.ui.theme.VibezTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : FragmentActivity() {

    private val container get() = (application as VibezApp).container

    /** Requests (e.g. from the notification) to show the terminal; buffered until the UI collects them. */
    private val openTerminalRequests = Channel<Unit>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val controller = TerminalController(this, container.sessions, container.settings)

        setContent {
            val settings by container.settings.settings.collectAsState()
            LaunchedEffect(settings.secureScreen) {
                if (settings.secureScreen) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            VibezTheme(settings.uiThemeMode) {
                if (container.appLock.isLocked) {
                    LockScreen(onUnlock = { container.appLock.unlock(this) })
                } else {
                    VibezNavigation(container, controller, openTerminalRequests.receiveAsFlow())
                }
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        container.appLock.onForeground()
    }

    override fun onStop() {
        super.onStop()
        container.appLock.onBackground()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_TERMINAL) openTerminalRequests.trySend(Unit)
    }

    companion object {
        const val ACTION_OPEN_TERMINAL = "com.vibez.terminal.action.OPEN_TERMINAL"
    }
}
