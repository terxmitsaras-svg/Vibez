package com.voxtype.keyboard.keyboard

/**
 * Tracks mutable keyboard state – shift, caps lock, current mode.
 * Routes each language code to its dedicated layout rows.
 */
class KeyboardState {

    enum class Mode { ALPHA, NUMBERS, SYMBOLS, EMOJI }
    enum class ShiftState { OFF, ON, CAPS_LOCK }

    var mode: Mode = Mode.ALPHA
        private set

    var shiftState: ShiftState = ShiftState.OFF
        private set

    val isUppercase: Boolean
        get() = shiftState != ShiftState.OFF

    // ─── Mode switching ───────────────────────────────────────────────────────

    fun switchToAlpha()   { mode = Mode.ALPHA   }
    fun switchToNumbers() { mode = Mode.NUMBERS }
    fun switchToSymbols() { mode = Mode.SYMBOLS }
    fun switchToEmoji()   { mode = Mode.EMOJI   }

    // ─── Shift / caps ────────────────────────────────────────────────────────

    fun onShiftPressed() {
        shiftState = when (shiftState) {
            ShiftState.OFF       -> ShiftState.ON
            ShiftState.ON        -> ShiftState.CAPS_LOCK
            ShiftState.CAPS_LOCK -> ShiftState.OFF
        }
    }

    /** Call after a character key is committed to auto-disable one-shot shift. */
    fun onCharCommitted() {
        if (shiftState == ShiftState.ON) shiftState = ShiftState.OFF
    }

    // ─── Layout routing ───────────────────────────────────────────────────────

    /**
     * Returns the correct key rows for the current [mode] and [languageCode].
     *
     * Language → layout mapping:
     *   en  → ENGLISH  (QWERTY)
     *   el  → GREEK
     *   es  → SPANISH  (QWERTY + ñ, ¿, ¡, accented vowels)
     *   fr  → FRENCH   (AZERTY)
     *   de  → GERMAN   (QWERTZ + ä, ö, ü, ß)
     *   it  → ITALIAN  (QWERTY + accented vowels)
     *   pt  → PORTUGUESE (QWERTY + ã, ç, accents)
     *   ar  → ARABIC
     *   hi  → HINDI    (Devanagari)
     *   zh  → CHINESE  (Pinyin / QWERTY)
     */
    fun getCurrentRows(languageCode: String): List<List<KeyData>> = when {
        mode == Mode.NUMBERS -> KeyboardLayout.NUMBER_ROWS
        mode == Mode.SYMBOLS -> KeyboardLayout.SYMBOL_ROWS
        else -> when (languageCode.lowercase().take(2)) {
            "el" -> KeyboardLayout.GREEK_ROWS
            "es" -> KeyboardLayout.SPANISH_ROWS
            "fr" -> KeyboardLayout.FRENCH_ROWS
            "de" -> KeyboardLayout.GERMAN_ROWS
            "it" -> KeyboardLayout.ITALIAN_ROWS
            "pt" -> KeyboardLayout.PORTUGUESE_ROWS
            "ar" -> KeyboardLayout.ARABIC_ROWS
            "hi" -> KeyboardLayout.HINDI_ROWS
            "zh" -> KeyboardLayout.CHINESE_ROWS
            else -> KeyboardLayout.ENGLISH_ROWS   // "en" and any unknown → English QWERTY
        }
    }
}
