package com.voxtype.keyboard.autocorrect

import android.content.Context
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import java.util.Locale

/**
 * AutocorrectEngine – bridges Android's built-in spell-check services to
 * provide autocorrect / predictive text suggestions.
 *
 * On Android 5+, the platform SpellCheckerService (backed by the active
 * keyboard language) provides spelling corrections. We supplement this with
 * a small in-memory frequency dictionary for the most common words.
 */
class AutocorrectEngine(private val context: Context) {

    // ─── Frequency dictionary (top English words as bootstrap) ────────────────

    private val commonWords = listOf(
        "the","be","to","of","and","a","in","that","have","it",
        "for","not","on","with","he","as","you","do","at","this",
        "but","his","by","from","they","we","say","her","she","or",
        "an","will","my","one","all","would","there","their","what",
        "so","up","out","if","about","who","get","which","go","me",
        "when","make","can","like","time","no","just","him","know",
        "take","people","into","year","your","good","some","could",
        "them","see","other","than","then","now","look","only","come",
        "its","over","think","also","back","after","use","two","how",
        "our","work","first","well","way","even","new","want","because",
        "any","these","give","day","most","us","thank","hello","please",
        "yes","sorry","okay","sure","great","love","need","here","there",
        "today","tomorrow","yesterday","morning","night","home","office",
        "message","call","send","meeting","schedule","help","question"
    )

    // ─── Spell checker session ────────────────────────────────────────────────

    private var spellSession: SpellCheckerSession? = null
    private var pendingCallback: ((List<String>) -> Unit)? = null

    private val sessionListener = object : SpellCheckerSession.SpellCheckerSessionListener {
        override fun onGetSuggestions(results: Array<out android.view.textservice.SuggestionsInfo>?) {
            val words = mutableListOf<String>()
            results?.forEach { info ->
                if (info.suggestionsCount > 0) {
                    for (i in 0 until minOf(info.suggestionsCount, 3)) {
                        words.add(info.getSuggestionAt(i))
                    }
                }
            }
            pendingCallback?.invoke(words.distinct())
        }

        override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
            // Not used in this implementation
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns up to 3 suggestions for [word] in the given language.
     * Falls back to prefix-matching against [commonWords] when the
     * spell-checker session is unavailable.
     */
    fun getSuggestions(word: String, languageCode: String): List<String> {
        if (word.length < 2) return emptyList()
        val lower = word.lowercase()
        // Prefix matches from the common-word list
        val prefixMatches = commonWords
            .filter { it.startsWith(lower) && it != lower }
            .take(3)

        // Also try spell-checker async if session available – results will come
        // on the next keystroke via onGetSuggestions, which is acceptable UX.
        requestSpellCheck(word, languageCode)

        return prefixMatches
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private fun requestSpellCheck(word: String, languageCode: String) {
        try {
            val tsm = context.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE)
                    as? TextServicesManager ?: return
            if (spellSession == null) {
                spellSession = tsm.newSpellCheckerSession(
                    null,
                    Locale.forLanguageTag(languageCode),
                    sessionListener,
                    true
                )
            }
            spellSession?.getSuggestions(TextInfo(word), 3)
        } catch (_: Exception) {
            // Spell checker may not be available on all devices
        }
    }

    fun destroy() {
        spellSession?.close()
        spellSession = null
    }
}
