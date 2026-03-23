package com.vibez.keyboard.voice

import com.vibez.keyboard.language.KeyboardLanguage

/**
 * VoiceCommandProcessor
 *
 * Processes voice commands like "comma", "full stop", "new line", "delete last sentence"
 * Supports English and Greek commands.
 */
object VoiceCommandProcessor {

    data class ProcessResult(
        val text: String,          // transformed text (empty if consumed as command)
        val command: VoiceCommand? // command detected
    )

    enum class VoiceCommand {
        NEW_LINE,
        DELETE_WORD,
        DELETE_SENTENCE,
        CLEAR_ALL,
        SUBMIT
    }

    // Punctuation map: spoken word → inserted character
    private val englishPunctuation = mapOf(
        "comma" to ", ",
        "period" to ". ",
        "full stop" to ". ",
        "dot" to ".",
        "exclamation mark" to "! ",
        "exclamation point" to "! ",
        "question mark" to "? ",
        "colon" to ": ",
        "semicolon" to "; ",
        "apostrophe" to "'",
        "open bracket" to "(",
        "close bracket" to ")",
        "open parenthesis" to "(",
        "close parenthesis" to ")",
        "dash" to " - ",
        "hyphen" to "-",
        "ellipsis" to "... ",
        "new paragraph" to "\n\n",
        "at sign" to "@",
        "hashtag" to "#",
        "percent" to "%",
        "ampersand" to " & ",
        "slash" to "/",
        "backslash" to "\\"
    )

    private val greekPunctuation = mapOf(
        "κόμμα" to ", ",
        "τελεία" to ". ",
        "θαυμαστικό" to "! ",
        "ερωτηματικό" to "; ",
        "άνω τελεία" to "· ",
        "δύο τελείες" to ": ",
        "παρένθεση ανοιχτή" to "(",
        "παρένθεση κλειστή" to ")",
        "παύλα" to " - ",
        "αποστρόφος" to "'",
        "νέα παράγραφος" to "\n\n"
    )

    // Commands map: spoken phrase → VoiceCommand
    private val englishCommands = mapOf(
        "new line" to VoiceCommand.NEW_LINE,
        "next line" to VoiceCommand.NEW_LINE,
        "delete word" to VoiceCommand.DELETE_WORD,
        "delete last word" to VoiceCommand.DELETE_WORD,
        "delete sentence" to VoiceCommand.DELETE_SENTENCE,
        "delete last sentence" to VoiceCommand.DELETE_SENTENCE,
        "clear all" to VoiceCommand.CLEAR_ALL,
        "clear everything" to VoiceCommand.CLEAR_ALL,
        "send" to VoiceCommand.SUBMIT,
        "submit" to VoiceCommand.SUBMIT,
        "press enter" to VoiceCommand.SUBMIT
    )

    private val greekCommands = mapOf(
        "νέα γραμμή" to VoiceCommand.NEW_LINE,
        "επόμενη γραμμή" to VoiceCommand.NEW_LINE,
        "διαγραφή λέξης" to VoiceCommand.DELETE_WORD,
        "διέγραψε τη λέξη" to VoiceCommand.DELETE_WORD,
        "διαγραφή πρότασης" to VoiceCommand.DELETE_SENTENCE,
        "διέγραψε την πρόταση" to VoiceCommand.DELETE_SENTENCE,
        "σβήσε όλα" to VoiceCommand.CLEAR_ALL,
        "αποστολή" to VoiceCommand.SUBMIT,
        "αποστολη" to VoiceCommand.SUBMIT
    )

    /**
     * Process transcribed text for commands and punctuation substitutions.
     * Returns the processed text and any detected command.
     */
    fun process(text: String, language: KeyboardLanguage): ProcessResult {
        val lower = text.lowercase().trim()

        // Check commands first
        val commands = if (language == KeyboardLanguage.GREEK) greekCommands else englishCommands
        for ((phrase, cmd) in commands) {
            if (lower == phrase || lower.endsWith(" $phrase") || lower == phrase) {
                val textWithoutCommand = text.substringBefore(phrase, "").trim()
                return ProcessResult(
                    text = processedPunctuation(textWithoutCommand, language),
                    command = cmd
                )
            }
        }

        // Process punctuation substitutions
        return ProcessResult(
            text = processedPunctuation(text, language),
            command = null
        )
    }

    private fun processedPunctuation(text: String, language: KeyboardLanguage): String {
        if (text.isBlank()) return text

        val punctMap = if (language == KeyboardLanguage.GREEK) greekPunctuation else englishPunctuation
        var result = text

        for ((word, replacement) in punctMap) {
            // Replace standalone word (surrounded by spaces or at boundaries)
            result = result.replace(Regex("(?i)\\b${Regex.escape(word)}\\b"), replacement)
        }

        // Capitalize first letter of sentences
        result = capitalizeSentences(result)

        return result
    }

    /**
     * Auto-capitalize the first letter after a sentence-ending punctuation.
     */
    private fun capitalizeSentences(text: String): String {
        if (text.isEmpty()) return text
        val sb = StringBuilder()
        var capitalizeNext = true
        for (ch in text) {
            if (capitalizeNext && ch.isLetter()) {
                sb.append(ch.uppercaseChar())
                capitalizeNext = false
            } else {
                sb.append(ch)
            }
            if (ch == '.' || ch == '!' || ch == '?') {
                capitalizeNext = true
            }
        }
        return sb.toString()
    }

    /**
     * Formats final text with proper spacing (trim extra spaces, etc.)
     */
    fun formatForInsertion(text: String): String {
        return text
            .replace(Regex("\\s{2,}"), " ")  // multiple spaces → single
            .replace(Regex("\\s([,\\.!\\?;:])"), "$1") // space before punct → remove
            .trim()
    }
}
