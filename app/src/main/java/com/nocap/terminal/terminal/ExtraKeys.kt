package com.nocap.terminal.terminal

import android.view.KeyEvent

enum class Modifier { CTRL, ALT, SHIFT, FN }

enum class ExtraKeyAction { KEYBOARD, PASTE, SNIPPETS }

/** A key on the extra keys bar above the keyboard. */
sealed class ExtraKey(val label: String) {
    class ModifierKey(val modifier: Modifier, label: String) : ExtraKey(label)

    /** Sends an Android key code, so the emulator encodes it for the current terminal mode. */
    class Code(val keyCode: Int, label: String, val repeatable: Boolean) : ExtraKey(label)

    class Text(val text: String) : ExtraKey(text)

    class Action(val action: ExtraKeyAction, label: String) : ExtraKey(label)
}

/**
 * Parses extra keys layouts: one row per line, keys separated by whitespace. Known names (ESC, CTRL,
 * UP, F1, PASTE, ...) are special keys; anything else is typed as text.
 */
object ExtraKeysLayout {

    private val named: Map<String, () -> ExtraKey> = buildMap {
        put("CTRL") { ExtraKey.ModifierKey(Modifier.CTRL, "CTRL") }
        put("ALT") { ExtraKey.ModifierKey(Modifier.ALT, "ALT") }
        put("SHIFT") { ExtraKey.ModifierKey(Modifier.SHIFT, "SHIFT") }
        put("FN") { ExtraKey.ModifierKey(Modifier.FN, "FN") }
        put("ESC") { ExtraKey.Code(KeyEvent.KEYCODE_ESCAPE, "ESC", repeatable = false) }
        put("TAB") { ExtraKey.Code(KeyEvent.KEYCODE_TAB, "TAB", repeatable = true) }
        put("ENTER") { ExtraKey.Code(KeyEvent.KEYCODE_ENTER, "↵", repeatable = true) }
        put("BKSP") { ExtraKey.Code(KeyEvent.KEYCODE_DEL, "⌫", repeatable = true) }
        put("DEL") { ExtraKey.Code(KeyEvent.KEYCODE_FORWARD_DEL, "DEL", repeatable = true) }
        put("INS") { ExtraKey.Code(KeyEvent.KEYCODE_INSERT, "INS", repeatable = false) }
        put("UP") { ExtraKey.Code(KeyEvent.KEYCODE_DPAD_UP, "↑", repeatable = true) }
        put("DOWN") { ExtraKey.Code(KeyEvent.KEYCODE_DPAD_DOWN, "↓", repeatable = true) }
        put("LEFT") { ExtraKey.Code(KeyEvent.KEYCODE_DPAD_LEFT, "←", repeatable = true) }
        put("RIGHT") { ExtraKey.Code(KeyEvent.KEYCODE_DPAD_RIGHT, "→", repeatable = true) }
        put("HOME") { ExtraKey.Code(KeyEvent.KEYCODE_MOVE_HOME, "HOME", repeatable = false) }
        put("END") { ExtraKey.Code(KeyEvent.KEYCODE_MOVE_END, "END", repeatable = false) }
        put("PGUP") { ExtraKey.Code(KeyEvent.KEYCODE_PAGE_UP, "PGUP", repeatable = true) }
        put("PGDN") { ExtraKey.Code(KeyEvent.KEYCODE_PAGE_DOWN, "PGDN", repeatable = true) }
        for (n in 1..12) put("F$n") { ExtraKey.Code(KeyEvent.KEYCODE_F1 + n - 1, "F$n", repeatable = false) }
        put("KEYBOARD") { ExtraKey.Action(ExtraKeyAction.KEYBOARD, "⌨") }
        put("PASTE") { ExtraKey.Action(ExtraKeyAction.PASTE, "PASTE") }
        put("SNIPPETS") { ExtraKey.Action(ExtraKeyAction.SNIPPETS, "{ }") }
    }

    /** Names accepted in layouts, for showing help to the user. */
    val keyNames: Set<String> get() = named.keys

    fun parse(layout: String): List<List<ExtraKey>> = layout.lines()
        .map { line -> line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() } }
        .filter { it.isNotEmpty() }
        .map { row -> row.map { token -> named[token.uppercase()]?.invoke() ?: ExtraKey.Text(token) } }
}
