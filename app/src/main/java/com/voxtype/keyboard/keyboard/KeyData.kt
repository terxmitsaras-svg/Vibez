package com.voxtype.keyboard.keyboard

/**
 * Represents a single key on the keyboard.
 */
data class KeyData(
    val code: Int,                      // Primary key code (Unicode for chars, negative for special)
    val label: String,                  // Display label
    val secondaryLabel: String = "",    // Secondary label shown on upper corner
    val type: KeyType = KeyType.CHAR,
    val widthFactor: Float = 1.0f,      // Width relative to standard key
    val codes: IntArray = intArrayOf()  // Alternative codes for multi-tap
) {
    companion object {
        // Special key codes
        const val CODE_BACKSPACE = -1
        const val CODE_SHIFT = -2
        const val CODE_SWITCH_ALPHA = -3
        const val CODE_SWITCH_NUMBERS = -4
        const val CODE_SWITCH_SYMBOLS = -5
        const val CODE_EMOJI = -6
        const val CODE_MIC = -7
        const val CODE_LANGUAGE = -8
        const val CODE_ENTER = 10
        const val CODE_SPACE = 32
        const val CODE_COMMA = 44
        const val CODE_PERIOD = 46
        const val CODE_CLIPBOARD = -9
        const val CODE_SETTINGS = -10
    }
}

enum class KeyType {
    CHAR,       // Regular character key
    BACKSPACE,  // Delete key
    SHIFT,      // Shift / caps lock
    ENTER,      // Enter / return
    SPACE,      // Spacebar
    SWITCH,     // Mode switch (alpha/numbers/symbols)
    EMOJI,      // Emoji picker
    MIC,        // Voice input
    LANGUAGE,   // Language switcher
    CLIPBOARD,  // Clipboard
    SETTINGS,   // Settings
    FUNCTIONAL  // Other functional keys
}
