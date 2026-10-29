package com.vibez.terminal.terminal

import com.termux.terminal.TerminalColors
import com.termux.terminal.TextStyle
import java.util.Properties

/** A terminal color scheme: the 16 ANSI colors plus foreground, background and cursor. */
class TerminalTheme(
    val name: String,
    val background: Int,
    val foreground: Int,
    val cursor: Int,
    /** ANSI colors 0-15: black, red, green, yellow, blue, magenta, cyan, white, then the bright variants. */
    val ansi: IntArray,
) {
    init {
        require(ansi.size == 16) { "A theme needs exactly 16 ANSI colors" }
    }

    val isDark: Boolean get() = TerminalColors.getPerceivedBrightnessOfColor(background) < 130

    /** Make this the theme new sessions and color resets fall back to. */
    fun applyAsDefault() {
        val properties = Properties()
        ansi.forEachIndexed { index, color -> properties["color$index"] = hex(color) }
        properties["foreground"] = hex(foreground)
        properties["background"] = hex(background)
        properties["cursor"] = hex(cursor)
        TerminalColors.COLOR_SCHEME.updateWith(properties)
    }

    /** Recolor one running session, keeping the 256-color palette entries above 15. */
    fun applyTo(colors: TerminalColors) {
        ansi.forEachIndexed { index, color -> colors.mCurrentColors[index] = color }
        colors.mCurrentColors[TextStyle.COLOR_INDEX_FOREGROUND] = foreground
        colors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND] = background
        colors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] = cursor
    }

    private fun hex(color: Int) = "#%06x".format(color and 0xFFFFFF)
}

object TerminalThemes {

    private fun theme(name: String, background: Long, foreground: Long, cursor: Long, vararg ansi: Long) =
        TerminalTheme(name, background.toInt(), foreground.toInt(), cursor.toInt(), ansi.map { it.toInt() }.toIntArray())

    val all: List<TerminalTheme> = listOf(
        theme(
            "Vibez Night", 0xFF0F1419, 0xFFE6EDF3, 0xFF7EE787,
            0xFF1B2127, 0xFFFF7B72, 0xFF7EE787, 0xFFE3B341, 0xFF79C0FF, 0xFFD2A8FF, 0xFF56D4DD, 0xFFC9D1D9,
            0xFF6E7681, 0xFFFFA198, 0xFFA5F3B0, 0xFFF2CC60, 0xFFA5D6FF, 0xFFE2C5FF, 0xFFB3F0FF, 0xFFFFFFFF,
        ),
        theme(
            "Dracula", 0xFF282A36, 0xFFF8F8F2, 0xFFF8F8F2,
            0xFF21222C, 0xFFFF5555, 0xFF50FA7B, 0xFFF1FA8C, 0xFFBD93F9, 0xFFFF79C6, 0xFF8BE9FD, 0xFFF8F8F2,
            0xFF6272A4, 0xFFFF6E6E, 0xFF69FF94, 0xFFFFFFA5, 0xFFD6ACFF, 0xFFFF92DF, 0xFFA4FFFF, 0xFFFFFFFF,
        ),
        theme(
            "Tokyo Night", 0xFF1A1B26, 0xFFC0CAF5, 0xFFC0CAF5,
            0xFF15161E, 0xFFF7768E, 0xFF9ECE6A, 0xFFE0AF68, 0xFF7AA2F7, 0xFFBB9AF7, 0xFF7DCFFF, 0xFFA9B1D6,
            0xFF414868, 0xFFF7768E, 0xFF9ECE6A, 0xFFE0AF68, 0xFF7AA2F7, 0xFFBB9AF7, 0xFF7DCFFF, 0xFFC0CAF5,
        ),
        theme(
            "Catppuccin Mocha", 0xFF1E1E2E, 0xFFCDD6F4, 0xFFF5E0DC,
            0xFF45475A, 0xFFF38BA8, 0xFFA6E3A1, 0xFFF9E2AF, 0xFF89B4FA, 0xFFF5C2E7, 0xFF94E2D5, 0xFFBAC2DE,
            0xFF585B70, 0xFFF38BA8, 0xFFA6E3A1, 0xFFF9E2AF, 0xFF89B4FA, 0xFFF5C2E7, 0xFF94E2D5, 0xFFA6ADC8,
        ),
        theme(
            "One Dark", 0xFF282C34, 0xFFABB2BF, 0xFF528BFF,
            0xFF282C34, 0xFFE06C75, 0xFF98C379, 0xFFE5C07B, 0xFF61AFEF, 0xFFC678DD, 0xFF56B6C2, 0xFFABB2BF,
            0xFF5C6370, 0xFFE06C75, 0xFF98C379, 0xFFE5C07B, 0xFF61AFEF, 0xFFC678DD, 0xFF56B6C2, 0xFFFFFFFF,
        ),
        theme(
            "Nord", 0xFF2E3440, 0xFFD8DEE9, 0xFFD8DEE9,
            0xFF3B4252, 0xFFBF616A, 0xFFA3BE8C, 0xFFEBCB8B, 0xFF81A1C1, 0xFFB48EAD, 0xFF88C0D0, 0xFFE5E9F0,
            0xFF4C566A, 0xFFBF616A, 0xFFA3BE8C, 0xFFEBCB8B, 0xFF81A1C1, 0xFFB48EAD, 0xFF8FBCBB, 0xFFECEFF4,
        ),
        theme(
            "Gruvbox Dark", 0xFF282828, 0xFFEBDBB2, 0xFFEBDBB2,
            0xFF282828, 0xFFCC241D, 0xFF98971A, 0xFFD79921, 0xFF458588, 0xFFB16286, 0xFF689D6A, 0xFFA89984,
            0xFF928374, 0xFFFB4934, 0xFFB8BB26, 0xFFFABD2F, 0xFF83A598, 0xFFD3869B, 0xFF8EC07C, 0xFFEBDBB2,
        ),
        theme(
            "Monokai", 0xFF272822, 0xFFF8F8F2, 0xFFF8F8F0,
            0xFF272822, 0xFFF92672, 0xFFA6E22E, 0xFFF4BF75, 0xFF66D9EF, 0xFFAE81FF, 0xFFA1EFE4, 0xFFF8F8F2,
            0xFF75715E, 0xFFF92672, 0xFFA6E22E, 0xFFF4BF75, 0xFF66D9EF, 0xFFAE81FF, 0xFFA1EFE4, 0xFFF9F8F5,
        ),
        theme(
            "Solarized Dark", 0xFF002B36, 0xFF839496, 0xFF93A1A1,
            0xFF073642, 0xFFDC322F, 0xFF859900, 0xFFB58900, 0xFF268BD2, 0xFFD33682, 0xFF2AA198, 0xFFEEE8D5,
            0xFF002B36, 0xFFCB4B16, 0xFF586E75, 0xFF657B83, 0xFF839496, 0xFF6C71C4, 0xFF93A1A1, 0xFFFDF6E3,
        ),
        theme(
            "Solarized Light", 0xFFFDF6E3, 0xFF657B83, 0xFF586E75,
            0xFF073642, 0xFFDC322F, 0xFF859900, 0xFFB58900, 0xFF268BD2, 0xFFD33682, 0xFF2AA198, 0xFFEEE8D5,
            0xFF002B36, 0xFFCB4B16, 0xFF586E75, 0xFF657B83, 0xFF839496, 0xFF6C71C4, 0xFF93A1A1, 0xFFFDF6E3,
        ),
        theme(
            "One Light", 0xFFFAFAFA, 0xFF383A42, 0xFF526FFF,
            0xFF383A42, 0xFFE45649, 0xFF50A14F, 0xFFC18401, 0xFF0184BC, 0xFFA626A4, 0xFF0997B3, 0xFFFAFAFA,
            0xFF4F525E, 0xFFE06C75, 0xFF98C379, 0xFFE5C07B, 0xFF61AFEF, 0xFFC678DD, 0xFF56B6C2, 0xFFFFFFFF,
        ),
        theme(
            "Classic", 0xFF000000, 0xFFFFFFFF, 0xFFFFFFFF,
            0xFF000000, 0xFFCD0000, 0xFF00CD00, 0xFFCDCD00, 0xFF6495ED, 0xFFCD00CD, 0xFF00CDCD, 0xFFE5E5E5,
            0xFF7F7F7F, 0xFFFF0000, 0xFF00FF00, 0xFFFFFF00, 0xFF5C5CFF, 0xFFFF00FF, 0xFF00FFFF, 0xFFFFFFFF,
        ),
    )

    val default: TerminalTheme = all.first()

    fun byName(name: String?): TerminalTheme = all.firstOrNull { it.name == name } ?: default
}
