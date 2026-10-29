package com.vibez.terminal.terminal

import android.content.Context
import android.util.Log
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import com.vibez.terminal.data.AppSettings
import com.vibez.terminal.data.SettingsRepository

enum class ModifierState { OFF, ONCE, LOCKED }

/**
 * Glue between a [TerminalView] and the rest of the app: sticky modifier keys from the extra keys
 * bar, volume keys as Ctrl/Fn, pinch-to-zoom, hardware keyboard shortcuts and the soft keyboard.
 */
class TerminalController(
    private val context: Context,
    private val sessions: SessionManager,
    private val settings: SettingsRepository,
) : TerminalViewClient {

    private val modifierStates = mutableStateMapOf<Modifier, ModifierState>()

    /** Whether text is being selected in the terminal. */
    var isSelectingText by mutableStateOf(false)
        private set

    var view: TerminalView? = null
        private set

    /** Called by the UI when the snippets picker should open. */
    var onShowSnippets: () -> Unit = {}

    private var volumeDownHeld = false
    private var volumeUpHeld = false

    fun bind(view: TerminalView) {
        this.view = view
        view.setTerminalViewClient(this)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        applyAppearance(settings.current)
        sessions.terminalView = view
    }

    fun unbind(view: TerminalView) {
        if (this.view === view) this.view = null
        if (sessions.terminalView === view) sessions.terminalView = null
    }

    fun applyAppearance(current: AppSettings) {
        val view = view ?: return
        view.setTextSize(spToPx(current.fontSizeSp))
        view.setTypeface(TerminalFonts.load(context))
        view.keepScreenOn = current.keepScreenOn
        sessions.onSettingsChanged()
    }

    fun modifierState(modifier: Modifier): ModifierState = modifierStates[modifier] ?: ModifierState.OFF

    /** Tap: toggle a one-shot modifier. Long press: lock it until tapped again. */
    fun toggleModifier(modifier: Modifier, lock: Boolean) {
        modifierStates[modifier] = when {
            lock -> if (modifierState(modifier) == ModifierState.LOCKED) ModifierState.OFF else ModifierState.LOCKED
            modifierState(modifier) == ModifierState.OFF -> ModifierState.ONCE
            else -> ModifierState.OFF
        }
    }

    /** Whether [modifier] applies to the key being processed. One-shot modifiers are used up. */
    private fun consumeModifier(modifier: Modifier): Boolean = when (modifierState(modifier)) {
        ModifierState.OFF -> false
        ModifierState.LOCKED -> true
        ModifierState.ONCE -> {
            modifierStates[modifier] = ModifierState.OFF
            true
        }
    }

    fun onExtraKey(key: ExtraKey) {
        val view = view ?: return
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        when (key) {
            is ExtraKey.ModifierKey -> toggleModifier(key.modifier, lock = false)
            is ExtraKey.Code -> sendKeyCode(view, key.keyCode)
            is ExtraKey.Text -> key.text.codePoints().forEach { view.inputCodePoint(it, false, false) }
            is ExtraKey.Action -> when (key.action) {
                ExtraKeyAction.KEYBOARD -> toggleKeyboard()
                ExtraKeyAction.PASTE -> view.currentSession?.let { sessions.onPasteTextFromClipboard(it) }
                ExtraKeyAction.SNIPPETS -> onShowSnippets()
            }
        }
    }

    private fun sendKeyCode(view: TerminalView, keyCode: Int) {
        var meta = 0
        if (consumeModifier(Modifier.CTRL)) meta = meta or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (consumeModifier(Modifier.ALT)) meta = meta or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        if (consumeModifier(Modifier.SHIFT)) meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        if (consumeModifier(Modifier.FN)) meta = meta or KeyEvent.META_FUNCTION_ON
        view.onKeyDown(keyCode, KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
    }

    fun showKeyboard() {
        val view = view ?: return
        view.requestFocus()
        context.getSystemService<InputMethodManager>()?.showSoftInput(view, 0)
    }

    fun hideKeyboard() {
        val view = view ?: return
        context.getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun toggleKeyboard() {
        val view = view ?: return
        val visible = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        if (visible) hideKeyboard() else showKeyboard()
    }

    fun changeFontSize(deltaSp: Int) {
        settings.update {
            it.copy(fontSizeSp = (it.fontSizeSp + deltaSp).coerceIn(AppSettings.MIN_FONT_SIZE, AppSettings.MAX_FONT_SIZE))
        }
        view?.setTextSize(spToPx(settings.current.fontSizeSp))
    }

    private fun spToPx(sp: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp.toFloat(), context.resources.displayMetrics).toInt()

    // region TerminalViewClient

    override fun onScale(scale: Float): Float {
        if (scale < 0.9f || scale > 1.1f) {
            changeFontSize(if (scale > 1f) 1 else -1)
            return 1f
        }
        return scale
    }

    override fun onSingleTapUp(e: MotionEvent) {
        showKeyboard()
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = settings.current.backKeyIsEscape

    override fun shouldEnforceCharBasedInput(): Boolean = settings.current.enforceCharBasedInput

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) {
        isSelectingText = copyMode
    }

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession?): Boolean {
        val tab = sessions.activeTab
        if (session != null && !session.isRunning && keyCode == KeyEvent.KEYCODE_ENTER && tab != null) {
            if (tab.kind == SessionKind.LOCAL && session.exitStatus != TerminalSession.EXIT_STATUS_START_FAILED) {
                sessions.close(tab.id)
            } else {
                sessions.restart(tab.id)
            }
            return true
        }

        if (settings.current.volumeKeysAsModifiers) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    volumeDownHeld = true
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    volumeUpHeld = true
                    return true
                }
            }
        }

        // Hardware keyboard shortcuts: Ctrl+Shift+...
        if (e.isCtrlPressed && e.isShiftPressed) {
            when (keyCode) {
                KeyEvent.KEYCODE_V -> session?.let { sessions.onPasteTextFromClipboard(it) }
                KeyEvent.KEYCODE_T -> sessions.openLocal()
                KeyEvent.KEYCODE_W -> tab?.let { sessions.close(it.id) }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_TAB -> sessions.selectRelative(1)
                KeyEvent.KEYCODE_DPAD_LEFT -> sessions.selectRelative(-1)
                KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_PLUS -> changeFontSize(1)
                KeyEvent.KEYCODE_MINUS -> changeFontSize(-1)
                else -> return false
            }
            return true
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean {
        if (settings.current.volumeKeysAsModifiers) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    volumeDownHeld = false
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    volumeUpHeld = false
                    return true
                }
            }
        }
        return false
    }

    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean = consumeModifier(Modifier.CTRL) || volumeDownHeld

    override fun readAltKey(): Boolean = consumeModifier(Modifier.ALT)

    override fun readShiftKey(): Boolean = consumeModifier(Modifier.SHIFT)

    override fun readFnKey(): Boolean = consumeModifier(Modifier.FN) || volumeUpHeld

    /** Volume up + key produces special keys, compatible with Termux. */
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean {
        if (!volumeUpHeld || session == null) return false
        val view = view ?: return false
        when (val char = Character.toLowerCase(codePoint).toChar()) {
            'h' -> session.write("~")
            'u' -> session.write("_")
            'l' -> session.write("|")
            'b', 'f', 'x' -> session.writeCodePoint(true, char.code)
            'k' -> toggleKeyboard()
            else -> view.handleKeyCode(FN_KEY_CODES[char] ?: return false, 0)
        }
        return true
    }

    override fun onEmulatorSet() {
        val view = view ?: return
        view.setTerminalCursorBlinkerRate(if (settings.current.cursorBlink) SessionManager.CURSOR_BLINK_RATE_MS else 0)
        view.setTerminalCursorBlinkerState(true, true)
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

    private companion object {
        val FN_KEY_CODES: Map<Char, Int> = buildMap {
            put('w', KeyEvent.KEYCODE_DPAD_UP)
            put('a', KeyEvent.KEYCODE_DPAD_LEFT)
            put('s', KeyEvent.KEYCODE_DPAD_DOWN)
            put('d', KeyEvent.KEYCODE_DPAD_RIGHT)
            put('p', KeyEvent.KEYCODE_PAGE_UP)
            put('n', KeyEvent.KEYCODE_PAGE_DOWN)
            put('t', KeyEvent.KEYCODE_TAB)
            put('i', KeyEvent.KEYCODE_INSERT)
            put('e', KeyEvent.KEYCODE_ESCAPE)
            for (digit in 1..9) put('0' + digit, KeyEvent.KEYCODE_F1 + digit - 1)
            put('0', KeyEvent.KEYCODE_F10)
        }
    }
}
