package com.voxtype.keyboard.keyboard

/**
 * Defines keyboard layouts for every supported language.
 *
 * Layouts:
 *  • English  → QWERTY
 *  • Spanish  → QWERTY + ñ, ¡, ¿, accented vowels
 *  • French   → AZERTY (standard French layout)
 *  • German   → QWERTZ + ä, ö, ü, ß
 *  • Italian  → QWERTY + accented vowels (à, è, é, ì, ò, ó, ù)
 *  • Portuguese → QWERTY + ã, â, ç, accents
 *  • Greek    → Greek alphabet
 *  • Arabic   → Arabic alphabet (28-char layout across 3 rows)
 *  • Hindi    → Devanagari (common 30-char consonants + vowels)
 *  • Chinese  → Pinyin (QWERTY) – system handles Han conversion
 *
 *  Numbers & Symbols rows are shared across all languages.
 */
object KeyboardLayout {

    // ═══════════════════════════════════════════════════════════════════════
    // SHARED – Numbers and Symbols
    // ═══════════════════════════════════════════════════════════════════════

    val NUMBER_ROWS: List<List<KeyData>> = listOf(
        listOf(
            KeyData('1'.code, "1"), KeyData('2'.code, "2"), KeyData('3'.code, "3"),
            KeyData('4'.code, "4"), KeyData('5'.code, "5"), KeyData('6'.code, "6"),
            KeyData('7'.code, "7"), KeyData('8'.code, "8"), KeyData('9'.code, "9"),
            KeyData('0'.code, "0")
        ),
        listOf(
            KeyData('-'.code, "-"), KeyData('/'.code, "/"), KeyData(':'.code, ":"),
            KeyData(';'.code, ";"), KeyData('('.code, "("), KeyData(')'.code, ")"),
            KeyData('$'.code, "$"), KeyData('&'.code, "&"), KeyData('@'.code, "@")
        ),
        listOf(
            KeyData(KeyData.CODE_SWITCH_SYMBOLS, "#+=", type = KeyType.SWITCH, widthFactor = 1.5f),
            KeyData('.'.code, "."), KeyData(','.code, ","), KeyData('?'.code, "?"),
            KeyData('!'.code, "!"), KeyData('\''.code, "'"), KeyData('"'.code, "\""),
            KeyData(KeyData.CODE_BACKSPACE, "⌫", type = KeyType.BACKSPACE, widthFactor = 1.5f)
        ),
        listOf(
            KeyData(KeyData.CODE_SWITCH_ALPHA, "ABC", type = KeyType.SWITCH, widthFactor = 1.5f),
            KeyData(KeyData.CODE_EMOJI, "☺", type = KeyType.EMOJI),
            KeyData(KeyData.CODE_SPACE, " ", type = KeyType.SPACE, widthFactor = 4.5f),
            KeyData(KeyData.CODE_ENTER, "↵", type = KeyType.ENTER, widthFactor = 1.5f)
        )
    )

    val SYMBOL_ROWS: List<List<KeyData>> = listOf(
        listOf(
            KeyData('['.code, "["), KeyData(']'.code, "]"), KeyData('{'.code, "{"),
            KeyData('}'.code, "}"), KeyData('#'.code, "#"), KeyData('%'.code, "%"),
            KeyData('^'.code, "^"), KeyData('*'.code, "*"), KeyData('+'.code, "+"),
            KeyData('='.code, "=")
        ),
        listOf(
            KeyData('_'.code, "_"), KeyData('\\'.code, "\\"), KeyData('|'.code, "|"),
            KeyData('~'.code, "~"), KeyData('<'.code, "<"), KeyData('>'.code, ">"),
            KeyData('€'.code, "€"), KeyData('£'.code, "£"), KeyData('¥'.code, "¥")
        ),
        listOf(
            KeyData(KeyData.CODE_SWITCH_NUMBERS, "123", type = KeyType.SWITCH, widthFactor = 1.5f),
            KeyData('.'.code, "."), KeyData(','.code, ","), KeyData('?'.code, "?"),
            KeyData('!'.code, "!"), KeyData('\''.code, "'"),
            KeyData(KeyData.CODE_BACKSPACE, "⌫", type = KeyType.BACKSPACE, widthFactor = 1.5f)
        ),
        listOf(
            KeyData(KeyData.CODE_SWITCH_ALPHA, "ABC", type = KeyType.SWITCH, widthFactor = 1.5f),
            KeyData(KeyData.CODE_EMOJI, "☺", type = KeyType.EMOJI),
            KeyData(KeyData.CODE_SPACE, " ", type = KeyType.SPACE, widthFactor = 4.5f),
            KeyData(KeyData.CODE_ENTER, "↵", type = KeyType.ENTER, widthFactor = 1.5f)
        )
    )

    // ─── Action Row (bottom row, shared by all alpha layouts) ─────────────────

    private fun actionRow(showMic: Boolean = true): List<KeyData> = listOf(
        KeyData(KeyData.CODE_SWITCH_NUMBERS, "?123", type = KeyType.SWITCH, widthFactor = 1.5f),
        KeyData(KeyData.CODE_LANGUAGE, "🌐", type = KeyType.LANGUAGE),
        KeyData(KeyData.CODE_EMOJI, "☺", type = KeyType.EMOJI),
        KeyData(KeyData.CODE_SPACE, " ", type = KeyType.SPACE, widthFactor = if (showMic) 3.5f else 4.5f),
        KeyData(KeyData.CODE_MIC, "🎤", type = KeyType.MIC),
        KeyData(KeyData.CODE_ENTER, "↵", type = KeyType.ENTER, widthFactor = 1.5f)
    )

    // ═══════════════════════════════════════════════════════════════════════
    // ENGLISH – standard QWERTY
    // ═══════════════════════════════════════════════════════════════════════

    val ENGLISH_ROWS: List<List<KeyData>> = listOf(
        listOf(
            KeyData('q'.code, "q", "1"), KeyData('w'.code, "w", "2"),
            KeyData('e'.code, "e", "3"), KeyData('r'.code, "r", "4"),
            KeyData('t'.code, "t", "5"), KeyData('y'.code, "y", "6"),
            KeyData('u'.code, "u", "7"), KeyData('i'.code, "i", "8"),
            KeyData('o'.code, "o", "9"), KeyData('p'.code, "p", "0")
        ),
        listOf(
            KeyData('a'.code, "a"), KeyData('s'.code, "s"), KeyData('d'.code, "d"),
            KeyData('f'.code, "f"), KeyData('g'.code, "g"), KeyData('h'.code, "h"),
            KeyData('j'.code, "j"), KeyData('k'.code, "k"), KeyData('l'.code, "l")
        ),
        listOf(
            KeyData(KeyData.CODE_SHIFT, "⇧", type = KeyType.SHIFT, widthFactor = 1.5f),
            KeyData('z'.code, "z"), KeyData('x'.code, "x"), KeyData('c'.code, "c"),
            KeyData('v'.code, "v"), KeyData('b'.code, "b"), KeyData('n'.code, "n"),
            KeyData('m'.code, "m"),
            KeyData(KeyData.CODE_BACKSPACE, "⌫", type = KeyType.BACKSPACE, widthFactor = 1.5f)
        ),
        actionRow()
    )

    // ═══════════════════════════════════════════════════════════════════════
    // SPANISH – QWERTY + ñ, ¿, ¡, accented vowels on long-press
    // ═══════════════════════════════════════════════════════════════════════

    val SPANISH_ROWS: List<List<KeyData>> = listOf(
        listOf(
            KeyData('q'.code, "q", "1"), KeyData('w'.code, "w", "2"),
            KeyData('e'.code, "e", "é"), KeyData('r'.code, "r", "4"),
            KeyData('t'.code, "t", "5"), KeyData('y'.code, "y", "6"),
            KeyData('u'.code, "u", "ú"), KeyData('i'.code, "i", "í"),
            KeyData('o'.code, "o", "ó"), KeyData('p'.code, "p", "0")
        ),
        listOf(
            KeyData('a'.code, "a", "á"), KeyData('s'.code, "s"),
            KeyData('d'.code, "d"), KeyData('f'.code, "f"),
            KeyData('g'.code, "g"), KeyData('h'.code, "h"),
            KeyData('j'.code, "j"), KeyData('k'.code, "k"),
            KeyData('l'.code, "l"), KeyData('ñ'.code, "ñ")
        ),
        listOf(
            KeyData(KeyData.CODE_SHIFT, "⇧", type = KeyType.SHIFT, widthFactor = 1.3f),
            KeyData('z'.code, "z"), KeyData('x'.code, "x"), KeyData('c'.code, "c"),
            KeyData('v'.code, "v"), KeyData('b'.code, "b"), KeyData('n'.code, "n"),
            KeyData('m'.code, "m"), KeyData('ü'.code, "ü"),
            KeyData(KeyData.CODE_BACKSPACE, "⌫", type = KeyType.BACKSPACE, widthFactor = 1.3f)
        ),
        listOf(
            KeyData(KeyData.CODE_SWITCH_NUMBERS, "?123", type = KeyType.SWITCH, widthFactor = 1.5f),
            KeyData(KeyData.CODE_LANGUAGE, "🌐", type = KeyType.LANGUAGE),
            KeyData('¿'.code, "¿"), KeyData('¡'.code, "¡"),
            KeyData(KeyData.CODE_SPACE, " ", type = KeyType.SPACE, widthFactor = 2.5f),
            KeyData(KeyData.CODE_MIC, "🎤", type = KeyType.MIC),
            KeyData(KeyData.CODE_ENTER, "↵", type = KeyType.ENTER, widthFactor = 1.5f)
        )
    )

    // ═══════════════════════════════════════════════════════════════════════
    // FRENCH – AZERTY layout
    // ═══════════════════════════════════════════════════════════════════════

    val FRENCH_ROWS: List<List<KeyData>> = listOf(
        listOf(
            KeyData('a'.code, "a", "1"), KeyData('z'.code, "z", "2"),
            KeyData('e'.code, "e", "é"), KeyData('r'.code, "r", "4"),
            KeyData('t'.code, "t", "5"), KeyData('y'.code, "y", "6"),
            KeyData('u'.code, "u", "ù"), KeyData('i'.code, "i", "8"),
            KeyData('o'.code, "o", "œ"), KeyData('p'.code, "p", "0")
        ),
        listOf(
            KeyData('q'.code, "q"), KeyData('s'.code, "s"),
            KeyData('d'.code, "d"), KeyData('f'.code, "f"),
            KeyData('g'.code, "g"), KeyData('h'.code, "h"),
            KeyData('j'.code, "j"), KeyData('k'.code, "k"),
            KeyData('l'.code, "l"), KeyData('m'.code, "m")
        ),
        listOf(
            KeyData(KeyData.CODE_SHIFT, "⇧", type = KeyType.SHIFT, widthFactor = 1.3f),
            KeyData('w'.code, "w"), KeyData('x'.code, "x"),
            KeyData('c'.code, "c", "ç"), KeyData('v'.code, "v"),
            KeyData('b'.code, "b"), KeyData('n'.code, "n"),
            KeyData('à'.code, "à"), KeyData('è'.code, "è"),
            KeyData(KeyData.CODE_BACKSPACE, "⌫", type = KeyType.BACKSPACE, widthFactor = 1.3f)
        ),
        listOf(
            KeyData(KeyData.CODE_SWITCH_NUMBERS, "?123", type = KeyType.SWITCH, widthFactor = 1.5f),
            KeyData(KeyData.CODE_LANGUAGE, "🌐", type = KeyType.LANGUAGE),
            KeyData(KeyData.CODE_EMOJI, "☺", type = KeyType.EMOJI),
            KeyData(KeyData.CODE_SPACE, " ", type = KeyType.SPACE, widthFactor = 3.5f),
            KeyData(KeyData.CODE_MIC, "🎤", type = KeyType.MIC),
            KeyData(KeyData.CODE_ENTER, "↵", type = KeyType.ENTER, widthFactor = 1.5f)
        )
    )

    // ═══════════════════════════════════════════════════════════════════════
    // GERMAN – QWERTZ + ä, ö, ü, ß
    // ═══════════════════════════════════════════════════════════════════════

    val GERMAN_ROWS: List<List<KeyData>> = listOf(
        listOf(
            KeyData('q'.code, "q", "1"), KeyData('w'.code, "w", "2"),
            KeyData('e'.code, "e", "€"), KeyData('r'.code, "r", "4"),
            KeyData('t'.code, "t", "5"), KeyData('z'.code, "z", "6"),
            KeyData('u'.code, "u", "7"), KeyData('i'.code, "i", "8"),
            KeyData('o'.code, "o", "9"), KeyData('p'.code, "p", "0")
        ),
        listOf(
            KeyData('a'.code, "a"), KeyData('s'.code, "s", "ß"),
            KeyData('d'.code, "d"), KeyData('f'.code, "f"),
            KeyData('g'.code, "g"), KeyData('h'.code, "h"),
            KeyData('j'.code, "j"), KeyData('k'.code, "k"),
            KeyData('l'.code, "l"), KeyData('ö'.code, "ö")
        ),
        listOf(
            KeyData(KeyData.CODE_SHIFT, "⇧", type = KeyType.SHIFT, widthFactor = 1.3f),
            KeyData('y'.code, "y"), KeyData('x'.code, "x"),
            KeyData('c'.code, "c"), KeyData('v'.code, "v"),
            KeyData('b'.code, "b"), KeyData('n'.code, "n"),
            KeyData('m'.code, "m"), KeyData('ä'.code, "ä"), KeyData('ü'.code, "ü"),
            KeyData(KeyData.CODE_BACKSPACE, "⌫", type = KeyType.BACKSPACE, widthFactor = 1.3f)
        ),
        listOf(
            KeyData(KeyData.CODE_SWITCH_NUMBERS, "?123", type = KeyType.SWITCH, widthFactor = 1.5f),
            KeyData(KeyData.CODE_LANGUAGE, "🌐", type = KeyType.LANGUAGE),
            KeyData(KeyData.CODE_EMOJI, "☺", type = KeyType.EMOJI),
            KeyData(KeyData.CODE_SPACE, " ", type = KeyType.SPACE, widthFactor = 3.5f),
            KeyData(KeyData.CODE_MIC, "🎤", type = KeyType.MIC),
            KeyData(KeyData.CODE_ENTER, "↵", type = KeyType.ENTER, widthFactor = 1.5f)
        )
    )

    // ═══════════════════════════════════════════════════════════════════════
    // ITALIAN – QWERTY + accented vowels à, è, é, ì, ò, ó, ù
    // ═══════════════════════════════════════════════════════════════════════

    val ITALIAN_ROWS: List<List<KeyData>> = listOf(
        listOf(
            KeyData('q'.code, "q", "1"), KeyData('w'.code, "w", "2"),
            KeyData('e'.code, "e", "è"), KeyData('r'.code, "r", "4"),
            KeyData('t'.code, "t", "5"), KeyData('y'.code, "y", "6"),
            KeyData('u'.code, "u", "ù"), KeyData('i'.code, "i", "ì"),
            KeyData('o'.code, "o", "ò"), KeyData('p'.code, "p", "0")
        ),
        listOf(
            KeyData('a'.code, "a", "à"), KeyData('s'.code, "s"),
            KeyData('d'.code, "d"), KeyData('f'.code, "f"),
            KeyData('g'.code, "g"), KeyData('h'.code, "h"),
            KeyData('j'.code, "j"), KeyData('k'.code, "k"),
            KeyData('l'.code, "l"), KeyData('é'.code, "é")
        ),
        listOf(
            KeyData(KeyData.CODE_SHIFT, "⇧", type = KeyType.SHIFT, widthFactor = 1.3f),
            KeyData('z'.code, "z"), KeyData('x'.code, "x"),
            KeyData('c'.code, "c"), KeyData('v'.code, "v"),
            KeyData('b'.code, "b"), KeyData('n'.code, "n"),
            KeyData('m'.code, "m"), KeyData('ó'.code, "ó"),
            KeyData(KeyData.CODE_BACKSPACE, "⌫", type = KeyType.BACKSPACE, widthFactor = 1.3f)
        ),
        actionRow()
    )

    // ═══════════════════════════════════════════════════════════════════════
    // PORTUGUESE – QWERTY + ã, â, ç, ô, õ, accents
    // ═══════════════════════════════════════════════════════════════════════

    val PORTUGUESE_ROWS: List<List<KeyData>> = listOf(
        listOf(
            KeyData('q'.code, "q", "1"), KeyData('w'.code, "w", "2"),
            KeyData('e'.code, "e", "ê"), KeyData('r'.code, "r", "4"),
            KeyData('t'.code, "t", "5"), KeyData('y'.code, "y", "6"),
            KeyData('u'.code, "u", "ú"), KeyData('i'.code, "i", "í"),
            KeyData('o'.code, "o", "ô"), KeyData('p'.code, "p", "0")
        ),
        listOf(
            KeyData('a'.code, "a", "ã"), KeyData('s'.code, "s"),
            KeyData('d'.code, "d"), KeyData('f'.code, "f"),
            KeyData('g'.code, "g"), KeyData('h'.code, "h"),
            KeyData('j'.code, "j"), KeyData('k'.code, "k"),
            KeyData('l'.code, "l"), KeyData('ç'.code, "ç")
        ),
        listOf(
            KeyData(KeyData.CODE_SHIFT, "⇧", type = KeyType.SHIFT, widthFactor = 1.3f),
            KeyData('z'.code, "z"), KeyData('x'.code, "x"),
            KeyData('c'.code, "c"), KeyData('v'.code, "v"),
            KeyData('b'.code, "b"), KeyData('n'.code, "n"),
            KeyData('m'.code, "m"), KeyData('õ'.code, "õ"),
            KeyData(KeyData.CODE_BACKSPACE, "⌫", type = KeyType.BACKSPACE, widthFactor = 1.3f)
        ),
        listOf(
            KeyData(KeyData.CODE_SWITCH_NUMBERS, "?123", type = KeyType.SWITCH, widthFactor = 1.5f),
            KeyData(KeyData.CODE_LANGUAGE, "🌐", type = KeyType.LANGUAGE),
            KeyData('á'.code, "á"), KeyData('é'.code, "é"),
            KeyData(KeyData.CODE_SPACE, " ", type = KeyType.SPACE, widthFactor = 2.5f),
            KeyData(KeyData.CODE_MIC, "🎤", type = KeyType.MIC),
            KeyData(KeyData.CODE_ENTER, "↵", type = KeyType.ENTER, widthFactor = 1.5f)
        )
    )

    // ═══════════════════════════════════════════════════════════════════════
    // GREEK – Greek alphabet
    // ═══════════════════════════════════════════════════════════════════════

    val GREEK_ROWS: List<List<KeyData>> = listOf(
        listOf(
            KeyData('ς'.code, "ς", "1"), KeyData('ε'.code, "ε", "2"),
            KeyData('ρ'.code, "ρ", "3"), KeyData('τ'.code, "τ", "4"),
            KeyData('υ'.code, "υ", "5"), KeyData('θ'.code, "θ", "6"),
            KeyData('ι'.code, "ι", "7"), KeyData('ο'.code, "ο", "8"),
            KeyData('π'.code, "π", "9"), KeyData(';'.code, ";", "0")
        ),
        listOf(
            KeyData('α'.code, "α"), KeyData('σ'.code, "σ"),
            KeyData('δ'.code, "δ"), KeyData('φ'.code, "φ"),
            KeyData('γ'.code, "γ"), KeyData('η'.code, "η"),
            KeyData('ξ'.code, "ξ"), KeyData('κ'.code, "κ"),
            KeyData('λ'.code, "λ")
        ),
        listOf(
            KeyData(KeyData.CODE_SHIFT, "⇧", type = KeyType.SHIFT, widthFactor = 1.5f),
            KeyData('ζ'.code, "ζ"), KeyData('χ'.code, "χ"),
            KeyData('ψ'.code, "ψ"), KeyData('ω'.code, "ω"),
            KeyData('β'.code, "β"), KeyData('ν'.code, "ν"),
            KeyData('μ'.code, "μ"),
            KeyData(KeyData.CODE_BACKSPACE, "⌫", type = KeyType.BACKSPACE, widthFactor = 1.5f)
        ),
        actionRow()
    )

    // ═══════════════════════════════════════════════════════════════════════
    // ARABIC – 28 Arabic letters across 3 rows (right-to-left script)
    // The system handles RTL rendering via layoutDirection.
    // ═══════════════════════════════════════════════════════════════════════

    val ARABIC_ROWS: List<List<KeyData>> = listOf(
        listOf(
            KeyData('ض'.code, "ض"), KeyData('ص'.code, "ص"),
            KeyData('ث'.code, "ث"), KeyData('ق'.code, "ق"),
            KeyData('ف'.code, "ف"), KeyData('غ'.code, "غ"),
            KeyData('ع'.code, "ع"), KeyData('ه'.code, "ه"),
            KeyData('خ'.code, "خ"), KeyData('ح'.code, "ح")
        ),
        listOf(
            KeyData('ش'.code, "ش"), KeyData('س'.code, "س"),
            KeyData('ي'.code, "ي"), KeyData('ب'.code, "ب"),
            KeyData('ل'.code, "ل"), KeyData('ا'.code, "ا"),
            KeyData('ت'.code, "ت"), KeyData('ن'.code, "ن"),
            KeyData('م'.code, "م"), KeyData('ك'.code, "ك")
        ),
        listOf(
            KeyData(KeyData.CODE_SHIFT, "⇧", type = KeyType.SHIFT, widthFactor = 1.3f),
            KeyData('ئ'.code, "ئ"), KeyData('ء'.code, "ء"),
            KeyData('ؤ'.code, "ؤ"), KeyData('ر'.code, "ر"),
            KeyData('ى'.code, "ى"), KeyData('ة'.code, "ة"),
            KeyData('و'.code, "و"), KeyData('ز'.code, "ز"),
            KeyData('ظ'.code, "ظ"),
            KeyData(KeyData.CODE_BACKSPACE, "⌫", type = KeyType.BACKSPACE, widthFactor = 1.3f)
        ),
        listOf(
            KeyData(KeyData.CODE_SWITCH_NUMBERS, "؟١٢٣", type = KeyType.SWITCH, widthFactor = 1.5f),
            KeyData(KeyData.CODE_LANGUAGE, "🌐", type = KeyType.LANGUAGE),
            KeyData('،'.code, "،"), KeyData('؟'.code, "؟"),
            KeyData(KeyData.CODE_SPACE, " ", type = KeyType.SPACE, widthFactor = 2.5f),
            KeyData(KeyData.CODE_MIC, "🎤", type = KeyType.MIC),
            KeyData(KeyData.CODE_ENTER, "↵", type = KeyType.ENTER, widthFactor = 1.5f)
        )
    )

    // ═══════════════════════════════════════════════════════════════════════
    // HINDI – Devanagari consonants (common layout)
    // 3 rows of consonants + vowel matras + special chars
    // ═══════════════════════════════════════════════════════════════════════

    val HINDI_ROWS: List<List<KeyData>> = listOf(
        listOf(
            KeyData('ौ'.code, "ौ"), KeyData('ै'.code, "ै"),
            KeyData('ा'.code, "ा"), KeyData('ी'.code, "ी"),
            KeyData('ू'.code, "ू"), KeyData('ब'.code, "ब"),
            KeyData('ह'.code, "ह"), KeyData('ग'.code, "ग"),
            KeyData('द'.code, "द"), KeyData('ज'.code, "ज")
        ),
        listOf(
            KeyData('ो'.code, "ो"), KeyData('े'.code, "े"),
            KeyData('्'.code, "्"), KeyData('ि'.code, "ि"),
            KeyData('ु'.code, "ु"), KeyData('प'.code, "प"),
            KeyData('र'.code, "र"), KeyData('क'.code, "क"),
            KeyData('त'.code, "त"), KeyData('च'.code, "च")
        ),
        listOf(
            KeyData(KeyData.CODE_SHIFT, "⇧", type = KeyType.SHIFT, widthFactor = 1.3f),
            KeyData('ं'.code, "ं"), KeyData('म'.code, "म"),
            KeyData('न'.code, "न"), KeyData('व'.code, "व"),
            KeyData('ल'.code, "ल"), KeyData('स'.code, "स"),
            KeyData(','.code, ","), KeyData('।'.code, "।"),
            KeyData(KeyData.CODE_BACKSPACE, "⌫", type = KeyType.BACKSPACE, widthFactor = 1.3f)
        ),
        listOf(
            KeyData(KeyData.CODE_SWITCH_NUMBERS, "?123", type = KeyType.SWITCH, widthFactor = 1.5f),
            KeyData(KeyData.CODE_LANGUAGE, "🌐", type = KeyType.LANGUAGE),
            KeyData('अ'.code, "अ"), KeyData('इ'.code, "इ"),
            KeyData(KeyData.CODE_SPACE, " ", type = KeyType.SPACE, widthFactor = 2.5f),
            KeyData(KeyData.CODE_MIC, "🎤", type = KeyType.MIC),
            KeyData(KeyData.CODE_ENTER, "↵", type = KeyType.ENTER, widthFactor = 1.5f)
        )
    )

    // ═══════════════════════════════════════════════════════════════════════
    // CHINESE – Pinyin input (QWERTY layout; OS handles Han conversion)
    // Tones and special chars on secondary labels.
    // ═══════════════════════════════════════════════════════════════════════

    val CHINESE_ROWS: List<List<KeyData>> = listOf(
        listOf(
            KeyData('q'.code, "q", "1"), KeyData('w'.code, "w", "2"),
            KeyData('e'.code, "e", "3"), KeyData('r'.code, "r", "4"),
            KeyData('t'.code, "t", "5"), KeyData('y'.code, "y", "6"),
            KeyData('u'.code, "u", "7"), KeyData('i'.code, "i", "8"),
            KeyData('o'.code, "o", "9"), KeyData('p'.code, "p", "0")
        ),
        listOf(
            KeyData('a'.code, "a"), KeyData('s'.code, "s"),
            KeyData('d'.code, "d"), KeyData('f'.code, "f"),
            KeyData('g'.code, "g"), KeyData('h'.code, "h"),
            KeyData('j'.code, "j"), KeyData('k'.code, "k"),
            KeyData('l'.code, "l")
        ),
        listOf(
            KeyData(KeyData.CODE_SHIFT, "⇧", type = KeyType.SHIFT, widthFactor = 1.5f),
            KeyData('z'.code, "z"), KeyData('x'.code, "x"),
            KeyData('c'.code, "c"), KeyData('v'.code, "v"),
            KeyData('b'.code, "b"), KeyData('n'.code, "n"),
            KeyData('m'.code, "m"),
            KeyData(KeyData.CODE_BACKSPACE, "⌫", type = KeyType.BACKSPACE, widthFactor = 1.5f)
        ),
        listOf(
            KeyData(KeyData.CODE_SWITCH_NUMBERS, "?123", type = KeyType.SWITCH, widthFactor = 1.5f),
            KeyData(KeyData.CODE_LANGUAGE, "🌐", type = KeyType.LANGUAGE),
            KeyData('，'.code, "，"),
            KeyData(KeyData.CODE_SPACE, " ", type = KeyType.SPACE, widthFactor = 3.5f),
            KeyData(KeyData.CODE_MIC, "🎤", type = KeyType.MIC),
            KeyData(KeyData.CODE_ENTER, "↵", type = KeyType.ENTER, widthFactor = 1.5f)
        )
    )

    // ═══════════════════════════════════════════════════════════════════════
    // QWERTY_ROWS alias – kept for backward compatibility; uses English layout
    // ═══════════════════════════════════════════════════════════════════════

    val QWERTY_ROWS: List<List<KeyData>> get() = ENGLISH_ROWS
}
