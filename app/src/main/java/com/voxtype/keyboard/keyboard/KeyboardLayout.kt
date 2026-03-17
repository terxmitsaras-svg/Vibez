package com.voxtype.keyboard.keyboard

/**
 * Defines keyboard layouts for different modes (QWERTY, Numbers, Symbols).
 * Each row is a list of KeyData objects.
 */
object KeyboardLayout {

    // ─── QWERTY / ALPHA LAYOUT ────────────────────────────────────────────────

    val QWERTY_ROWS: List<List<KeyData>> = listOf(
        // Row 0 – top letter row
        listOf(
            KeyData('q'.code, "q", "1"),
            KeyData('w'.code, "w", "2"),
            KeyData('e'.code, "e", "3"),
            KeyData('r'.code, "r", "4"),
            KeyData('t'.code, "t", "5"),
            KeyData('y'.code, "y", "6"),
            KeyData('u'.code, "u", "7"),
            KeyData('i'.code, "i", "8"),
            KeyData('o'.code, "o", "9"),
            KeyData('p'.code, "p", "0")
        ),
        // Row 1 – middle letter row
        listOf(
            KeyData('a'.code, "a"),
            KeyData('s'.code, "s"),
            KeyData('d'.code, "d"),
            KeyData('f'.code, "f"),
            KeyData('g'.code, "g"),
            KeyData('h'.code, "h"),
            KeyData('j'.code, "j"),
            KeyData('k'.code, "k"),
            KeyData('l'.code, "l")
        ),
        // Row 2 – shift + bottom letters + backspace
        listOf(
            KeyData(KeyData.CODE_SHIFT, "⇧", type = KeyType.SHIFT, widthFactor = 1.5f),
            KeyData('z'.code, "z"),
            KeyData('x'.code, "x"),
            KeyData('c'.code, "c"),
            KeyData('v'.code, "v"),
            KeyData('b'.code, "b"),
            KeyData('n'.code, "n"),
            KeyData('m'.code, "m"),
            KeyData(KeyData.CODE_BACKSPACE, "⌫", type = KeyType.BACKSPACE, widthFactor = 1.5f)
        ),
        // Row 3 – action row
        listOf(
            KeyData(KeyData.CODE_SWITCH_NUMBERS, "?123", type = KeyType.SWITCH, widthFactor = 1.5f),
            KeyData(KeyData.CODE_LANGUAGE, "🌐", type = KeyType.LANGUAGE),
            KeyData(KeyData.CODE_EMOJI, "☺", type = KeyType.EMOJI),
            KeyData(KeyData.CODE_SPACE, " ", type = KeyType.SPACE, widthFactor = 4.0f),
            KeyData(KeyData.CODE_MIC, "🎤", type = KeyType.MIC),
            KeyData(KeyData.CODE_ENTER, "↵", type = KeyType.ENTER, widthFactor = 1.5f)
        )
    )

    // ─── NUMBER LAYOUT ────────────────────────────────────────────────────────

    val NUMBER_ROWS: List<List<KeyData>> = listOf(
        listOf(
            KeyData('1'.code, "1"),
            KeyData('2'.code, "2"),
            KeyData('3'.code, "3"),
            KeyData('4'.code, "4"),
            KeyData('5'.code, "5"),
            KeyData('6'.code, "6"),
            KeyData('7'.code, "7"),
            KeyData('8'.code, "8"),
            KeyData('9'.code, "9"),
            KeyData('0'.code, "0")
        ),
        listOf(
            KeyData('-'.code, "-"),
            KeyData('/'.code, "/"),
            KeyData(':'.code, ":"),
            KeyData(';'.code, ";"),
            KeyData('('.code, "("),
            KeyData(')'.code, ")"),
            KeyData('$'.code, "$"),
            KeyData('&'.code, "&"),
            KeyData('@'.code, "@")
        ),
        listOf(
            KeyData(KeyData.CODE_SWITCH_SYMBOLS, "#+=", type = KeyType.SWITCH, widthFactor = 1.5f),
            KeyData('.'.code, "."),
            KeyData(','.code, ","),
            KeyData('?'.code, "?"),
            KeyData('!'.code, "!"),
            KeyData('\''.code, "'"),
            KeyData('"'.code, "\""),
            KeyData(KeyData.CODE_BACKSPACE, "⌫", type = KeyType.BACKSPACE, widthFactor = 1.5f)
        ),
        listOf(
            KeyData(KeyData.CODE_SWITCH_ALPHA, "ABC", type = KeyType.SWITCH, widthFactor = 1.5f),
            KeyData(KeyData.CODE_EMOJI, "☺", type = KeyType.EMOJI),
            KeyData(KeyData.CODE_SPACE, " ", type = KeyType.SPACE, widthFactor = 4.5f),
            KeyData(KeyData.CODE_ENTER, "↵", type = KeyType.ENTER, widthFactor = 1.5f)
        )
    )

    // ─── SYMBOL LAYOUT ────────────────────────────────────────────────────────

    val SYMBOL_ROWS: List<List<KeyData>> = listOf(
        listOf(
            KeyData('['.code, "["),
            KeyData(']'.code, "]"),
            KeyData('{'.code, "{"),
            KeyData('}'.code, "}"),
            KeyData('#'.code, "#"),
            KeyData('%'.code, "%"),
            KeyData('^'.code, "^"),
            KeyData('*'.code, "*"),
            KeyData('+'.code, "+"),
            KeyData('='.code, "=")
        ),
        listOf(
            KeyData('_'.code, "_"),
            KeyData('\\'.code, "\\"),
            KeyData('|'.code, "|"),
            KeyData('~'.code, "~"),
            KeyData('<'.code, "<"),
            KeyData('>'.code, ">"),
            KeyData('€'.code, "€"),
            KeyData('£'.code, "£"),
            KeyData('¥'.code, "¥")
        ),
        listOf(
            KeyData(KeyData.CODE_SWITCH_NUMBERS, "123", type = KeyType.SWITCH, widthFactor = 1.5f),
            KeyData('.'.code, "."),
            KeyData(','.code, ","),
            KeyData('?'.code, "?"),
            KeyData('!'.code, "!"),
            KeyData('\''.code, "'"),
            KeyData(KeyData.CODE_BACKSPACE, "⌫", type = KeyType.BACKSPACE, widthFactor = 1.5f)
        ),
        listOf(
            KeyData(KeyData.CODE_SWITCH_ALPHA, "ABC", type = KeyType.SWITCH, widthFactor = 1.5f),
            KeyData(KeyData.CODE_EMOJI, "☺", type = KeyType.EMOJI),
            KeyData(KeyData.CODE_SPACE, " ", type = KeyType.SPACE, widthFactor = 4.5f),
            KeyData(KeyData.CODE_ENTER, "↵", type = KeyType.ENTER, widthFactor = 1.5f)
        )
    )

    // ─── GREEK LAYOUT ─────────────────────────────────────────────────────────

    val GREEK_ROWS: List<List<KeyData>> = listOf(
        listOf(
            KeyData('ς'.code, "ς", "1"),
            KeyData('ε'.code, "ε", "2"),
            KeyData('ρ'.code, "ρ", "3"),
            KeyData('τ'.code, "τ", "4"),
            KeyData('υ'.code, "υ", "5"),
            KeyData('θ'.code, "θ", "6"),
            KeyData('ι'.code, "ι", "7"),
            KeyData('ο'.code, "ο", "8"),
            KeyData('π'.code, "π", "9"),
            KeyData(';'.code, ";", "0")
        ),
        listOf(
            KeyData('α'.code, "α"),
            KeyData('σ'.code, "σ"),
            KeyData('δ'.code, "δ"),
            KeyData('φ'.code, "φ"),
            KeyData('γ'.code, "γ"),
            KeyData('η'.code, "η"),
            KeyData('ξ'.code, "ξ"),
            KeyData('κ'.code, "κ"),
            KeyData('λ'.code, "λ")
        ),
        listOf(
            KeyData(KeyData.CODE_SHIFT, "⇧", type = KeyType.SHIFT, widthFactor = 1.5f),
            KeyData('ζ'.code, "ζ"),
            KeyData('χ'.code, "χ"),
            KeyData('ψ'.code, "ψ"),
            KeyData('ω'.code, "ω"),
            KeyData('β'.code, "β"),
            KeyData('ν'.code, "ν"),
            KeyData('μ'.code, "μ"),
            KeyData(KeyData.CODE_BACKSPACE, "⌫", type = KeyType.BACKSPACE, widthFactor = 1.5f)
        ),
        listOf(
            KeyData(KeyData.CODE_SWITCH_NUMBERS, "?123", type = KeyType.SWITCH, widthFactor = 1.5f),
            KeyData(KeyData.CODE_LANGUAGE, "🌐", type = KeyType.LANGUAGE),
            KeyData(KeyData.CODE_EMOJI, "☺", type = KeyType.EMOJI),
            KeyData(KeyData.CODE_SPACE, " ", type = KeyType.SPACE, widthFactor = 4.0f),
            KeyData(KeyData.CODE_MIC, "🎤", type = KeyType.MIC),
            KeyData(KeyData.CODE_ENTER, "↵", type = KeyType.ENTER, widthFactor = 1.5f)
        )
    )
}
