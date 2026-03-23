package com.vibez.keyboard.utils

import android.content.Context
import com.vibez.keyboard.language.KeyboardLanguage
import com.vibez.keyboard.theme.KeyboardTheme

/**
 * PreferencesManager — Persistent user preferences using SharedPreferences.
 */
class PreferencesManager(context: Context) {

    private val prefs = context.getSharedPreferences("vibez_prefs", Context.MODE_PRIVATE)

    fun getLanguage(): KeyboardLanguage {
        val name = prefs.getString("language", KeyboardLanguage.ENGLISH.name) ?: return KeyboardLanguage.ENGLISH
        return try {
            KeyboardLanguage.valueOf(name)
        } catch (e: IllegalArgumentException) {
            KeyboardLanguage.ENGLISH
        }
    }

    fun saveLanguage(language: KeyboardLanguage) {
        prefs.edit().putString("language", language.name).apply()
    }

    fun getTheme(): KeyboardTheme {
        val name = prefs.getString("theme", KeyboardTheme.DARK.name) ?: return KeyboardTheme.DARK
        return try {
            KeyboardTheme.valueOf(name)
        } catch (e: IllegalArgumentException) {
            KeyboardTheme.DARK
        }
    }

    fun saveTheme(theme: KeyboardTheme) {
        prefs.edit().putString("theme", theme.name).apply()
    }

    fun isAutoTheme(): Boolean = prefs.getBoolean("auto_theme", true)
    fun setAutoTheme(auto: Boolean) { prefs.edit().putBoolean("auto_theme", auto).apply() }

    fun isHapticEnabled(): Boolean = prefs.getBoolean("haptic_enabled", true)
    fun setHapticEnabled(enabled: Boolean) { prefs.edit().putBoolean("haptic_enabled", enabled).apply() }

    fun isSoundEnabled(): Boolean = prefs.getBoolean("sound_enabled", false)
    fun setSoundEnabled(enabled: Boolean) { prefs.edit().putBoolean("sound_enabled", enabled).apply() }

    fun isAutoCorrectEnabled(): Boolean = prefs.getBoolean("auto_correct", true)
    fun setAutoCorrectEnabled(enabled: Boolean) { prefs.edit().putBoolean("auto_correct", enabled).apply() }

    fun getVoiceLanguage(): KeyboardLanguage {
        val name = prefs.getString("voice_language", null) ?: return getLanguage()
        return try {
            KeyboardLanguage.valueOf(name)
        } catch (e: IllegalArgumentException) {
            getLanguage()
        }
    }

    fun saveVoiceLanguage(language: KeyboardLanguage) {
        prefs.edit().putString("voice_language", language.name).apply()
    }

    fun isOfflineVoiceEnabled(): Boolean = prefs.getBoolean("offline_voice", true)
    fun setOfflineVoiceEnabled(enabled: Boolean) { prefs.edit().putBoolean("offline_voice", enabled).apply() }

    fun isPunctuationAutoInsert(): Boolean = prefs.getBoolean("punctuation_auto_insert", true)
    fun setPunctuationAutoInsert(enabled: Boolean) { prefs.edit().putBoolean("punctuation_auto_insert", enabled).apply() }

    fun hasSeenSetupGuide(): Boolean = prefs.getBoolean("setup_guide_seen", false)
    fun markSetupGuideSeen() { prefs.edit().putBoolean("setup_guide_seen", true).apply() }
}
