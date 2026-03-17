package com.voxtype.keyboard.keyboard

/**
 * Tracks mutable keyboard state – shift, caps lock, current mode, etc.
 */
class KeyboardState {

    enum class Mode { ALPHA, NUMBERS, SYMBOLS, EMOJI }
    enum class ShiftState { OFF, ON, CAPS_LOCK }

    var mode: Mode = Mode.ALPHA
        private set

    var shiftState: ShiftState = ShiftState.OFF
        private set

    /** True when an uppercase letter should be committed. */
    val isUppercase: Boolean
        get() = shiftState != ShiftState.OFF

    // ─── Mode switching ───────────────────────────────────────────────────────

    fun switchToAlpha() { mode = Mode.ALPHA }
    fun switchToNumbers() { mode = Mode.NUMBERS }
    fun switchToSymbols() { mode = Mode.SYMBOLS }
    fun switchToEmoji() { mode = Mode.EMOJI }

    // ─── Shift / caps ────────────────────────────────────────────────────────

    fun onShiftPressed() {
        shiftState = when (shiftState) {
            ShiftState.OFF -> ShiftState.ON
            ShiftState.ON -> ShiftState.CAPS_LOCK
            ShiftState.CAPS_LOCK -> ShiftState.OFF
        }
    }

    /** Call after a character key is committed to auto-disable one-shot shift. */
    fun onCharCommitted() {
        if (shiftState == ShiftState.ON) {
            shiftState = ShiftState.OFF
        }
    }

    // ─── Current layout rows ─────────────────────────────────────────────────

    fun getCurrentRows(languageCode: String): List<List<KeyData>> = when {
        mode == Mode.NUMBERS -> KeyboardLayout.NUMBER_ROWS
        mode == Mode.SYMBOLS -> KeyboardLayout.SYMBOL_ROWS
        languageCode == "el" -> KeyboardLayout.GREEK_ROWS
        else -> KeyboardLayout.QWERTY_ROWS
    }
}
