package com.voxtype.keyboard.lang

import android.content.Context
import android.content.SharedPreferences

/**
 * LanguageManager – manages the active keyboard language and cycling through
 * user-selected languages.
 *
 * Persists the last-used language to SharedPreferences so the selection
 * survives process death.
 */
class LanguageManager(context: Context) {

    data class Language(
        val code: String,      // ISO 639-1 code used internally  e.g. "en", "el"
        val bcp47: String,     // BCP-47 tag for SpeechRecognizer  e.g. "en-US"
        val displayName: String,
        val flagEmoji: String
    )

    // ─── Supported languages ──────────────────────────────────────────────────

    val supportedLanguages: List<Language> = listOf(
        Language("en", "en-US", "English",    "🇺🇸"),
        Language("el", "el-GR", "Greek",      "🇬🇷"),
        Language("es", "es-ES", "Spanish",    "🇪🇸"),
        Language("fr", "fr-FR", "French",     "🇫🇷"),
        Language("de", "de-DE", "German",     "🇩🇪"),
        Language("it", "it-IT", "Italian",    "🇮🇹"),
        Language("pt", "pt-BR", "Portuguese", "🇧🇷"),
        Language("ar", "ar",    "Arabic",     "🇸🇦"),
        Language("hi", "hi-IN", "Hindi",      "🇮🇳"),
        Language("zh", "zh-CN", "Chinese",    "🇨🇳")
    )

    // ─── Persistence ──────────────────────────────────────────────────────────

    private val prefs: SharedPreferences =
        context.getSharedPreferences("voxtype_lang", Context.MODE_PRIVATE)

    private var currentIndex: Int = run {
        val saved = prefs.getString("lang_code", "en") ?: "en"
        supportedLanguages.indexOfFirst { it.code == saved }.coerceAtLeast(0)
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    fun getCurrentLanguage(): Language = supportedLanguages[currentIndex]

    fun getCurrentLanguageCode(): String = getCurrentLanguage().code

    fun getCurrentBcp47(): String = getCurrentLanguage().bcp47

    fun switchToNextLanguage() {
        currentIndex = (currentIndex + 1) % supportedLanguages.size
        persist()
    }

    fun setLanguage(code: String) {
        val idx = supportedLanguages.indexOfFirst { it.code == code }
        if (idx >= 0) {
            currentIndex = idx
            persist()
        }
    }

    fun isRtl(): Boolean = getCurrentLanguage().code == "ar"

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun persist() {
        prefs.edit().putString("lang_code", getCurrentLanguageCode()).apply()
    }
}
