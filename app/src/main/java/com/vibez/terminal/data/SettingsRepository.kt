package com.vibez.terminal.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
enum class CursorStyle { BLOCK, UNDERLINE, BAR }

@Serializable
enum class BellMode { VIBRATE, VISUAL, OFF }

@Serializable
enum class UiThemeMode { SYSTEM, DARK, LIGHT }

@Serializable
data class AppSettings(
    val terminalTheme: String = "Vibez Night",
    val fontSizeSp: Int = 14,
    val cursorStyle: CursorStyle = CursorStyle.BLOCK,
    val cursorBlink: Boolean = true,
    val scrollbackLines: Int = 5000,
    val showExtraKeys: Boolean = true,
    val extraKeysLayout: String = DEFAULT_EXTRA_KEYS,
    val keepScreenOn: Boolean = false,
    val bellMode: BellMode = BellMode.VIBRATE,
    /** Volume down acts as Ctrl and volume up as Fn while the terminal is focused, like Termux. */
    val volumeKeysAsModifiers: Boolean = true,
    val backKeyIsEscape: Boolean = false,
    /** Work around keyboards that do not deliver key events properly by disabling suggestions. */
    val enforceCharBasedInput: Boolean = true,
    /** Close a local session automatically when its shell exits successfully. */
    val closeLocalSessionOnExit: Boolean = true,
    val localShell: String = DEFAULT_SHELL,
    val uiThemeMode: UiThemeMode = UiThemeMode.SYSTEM,
    val appLock: Boolean = false,
    /** Hide the app content from screenshots, screen recordings and the recent apps preview. */
    val secureScreen: Boolean = false,
    /** Lock again after the app has been in the background this long. */
    val appLockTimeoutSeconds: Int = 60,
) {
    companion object {
        const val DEFAULT_SHELL = "/system/bin/sh"
        const val MIN_FONT_SIZE = 6
        const val MAX_FONT_SIZE = 40
        val DEFAULT_EXTRA_KEYS = """
            ESC / - HOME UP END PGUP
            TAB CTRL ALT LEFT DOWN RIGHT PGDN
        """.trimIndent()
    }
}

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    val settings: StateFlow<AppSettings> = state.asStateFlow()

    val current: AppSettings get() = state.value

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(state.value)
        if (updated == state.value) return
        state.value = updated
        prefs.edit { putString(KEY, StoreJson.encodeToString(AppSettings.serializer(), updated)) }
    }

    private fun read(): AppSettings {
        val json = prefs.getString(KEY, null) ?: return AppSettings()
        return try {
            StoreJson.decodeFromString(AppSettings.serializer(), json)
        } catch (e: Exception) {
            Log.e("SettingsRepository", "Resetting unreadable settings", e)
            AppSettings()
        }
    }

    private companion object {
        const val KEY = "settings_json"
    }
}
