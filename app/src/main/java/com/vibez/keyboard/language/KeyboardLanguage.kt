package com.vibez.keyboard.language

/**
 * Keyboard layouts for 12 languages.
 * Each language defines its own key rows and locale for speech recognition.
 */
enum class KeyboardLanguage(
    val displayName: String,
    val locale: String,
    val spaceName: String,
    val row1: List<String>,
    val row2: List<String>,
    val row3: List<String>,
    val row1Numbers: List<String>,
    val row2Numbers: List<String>,
    val flagEmoji: String
) {
    ENGLISH(
        displayName = "English",
        locale = "en-US",
        spaceName = "space",
        row1 = listOf("q","w","e","r","t","y","u","i","o","p"),
        row2 = listOf("a","s","d","f","g","h","j","k","l"),
        row3 = listOf("z","x","c","v","b","n","m"),
        row1Numbers = listOf("1","2","3","4","5","6","7","8","9","0"),
        row2Numbers = listOf("@","#","$","%","&","-","+","(",")","\""),
        flagEmoji = "🇬🇧"
    ),

    GREEK(
        displayName = "Ελληνικά",
        locale = "el-GR",
        spaceName = "κενό",
        row1 = listOf("ς","ε","ρ","τ","υ","θ","ι","ο","π"),
        row2 = listOf("α","σ","δ","φ","γ","η","ξ","κ","λ"),
        row3 = listOf("ζ","χ","ψ","ω","β","ν","μ"),
        row1Numbers = listOf("1","2","3","4","5","6","7","8","9","0"),
        row2Numbers = listOf("@","#","€","%","&","-","+","(",")","\""),
        flagEmoji = "🇬🇷"
    ),

    SPANISH(
        displayName = "Español",
        locale = "es-ES",
        spaceName = "espacio",
        row1 = listOf("q","w","e","r","t","y","u","i","o","p"),
        row2 = listOf("a","s","d","f","g","h","j","k","l","ñ"),
        row3 = listOf("z","x","c","v","b","n","m"),
        row1Numbers = listOf("1","2","3","4","5","6","7","8","9","0"),
        row2Numbers = listOf("@","#","€","%","&","-","+","(",")","\""),
        flagEmoji = "🇪🇸"
    ),

    FRENCH(
        displayName = "Français",
        locale = "fr-FR",
        spaceName = "espace",
        row1 = listOf("a","z","e","r","t","y","u","i","o","p"),
        row2 = listOf("q","s","d","f","g","h","j","k","l","m"),
        row3 = listOf("w","x","c","v","b","n"),
        row1Numbers = listOf("1","2","3","4","5","6","7","8","9","0"),
        row2Numbers = listOf("@","#","€","%","&","-","+","(",")","\""),
        flagEmoji = "🇫🇷"
    ),

    GERMAN(
        displayName = "Deutsch",
        locale = "de-DE",
        spaceName = "Leerzeichen",
        row1 = listOf("q","w","e","r","t","z","u","i","o","p","ü"),
        row2 = listOf("a","s","d","f","g","h","j","k","l","ö","ä"),
        row3 = listOf("y","x","c","v","b","n","m"),
        row1Numbers = listOf("1","2","3","4","5","6","7","8","9","0"),
        row2Numbers = listOf("@","#","€","%","&","-","+","(",")","\""),
        flagEmoji = "🇩🇪"
    ),

    ITALIAN(
        displayName = "Italiano",
        locale = "it-IT",
        spaceName = "spazio",
        row1 = listOf("q","w","e","r","t","y","u","i","o","p"),
        row2 = listOf("a","s","d","f","g","h","j","k","l"),
        row3 = listOf("z","x","c","v","b","n","m"),
        row1Numbers = listOf("1","2","3","4","5","6","7","8","9","0"),
        row2Numbers = listOf("@","#","€","%","&","-","+","(",")","\""),
        flagEmoji = "🇮🇹"
    ),

    PORTUGUESE(
        displayName = "Português",
        locale = "pt-BR",
        spaceName = "espaço",
        row1 = listOf("q","w","e","r","t","y","u","i","o","p"),
        row2 = listOf("a","s","d","f","g","h","j","k","l","ç"),
        row3 = listOf("z","x","c","v","b","n","m"),
        row1Numbers = listOf("1","2","3","4","5","6","7","8","9","0"),
        row2Numbers = listOf("@","#","$","%","&","-","+","(",")","\""),
        flagEmoji = "🇧🇷"
    ),

    RUSSIAN(
        displayName = "Русский",
        locale = "ru-RU",
        spaceName = "пробел",
        row1 = listOf("й","ц","у","к","е","н","г","ш","щ","з","х"),
        row2 = listOf("ф","ы","в","а","п","р","о","л","д","ж","э"),
        row3 = listOf("я","ч","с","м","и","т","ь","б","ю"),
        row1Numbers = listOf("1","2","3","4","5","6","7","8","9","0"),
        row2Numbers = listOf("@","#","₽","%","&","-","+","(",")","\""),
        flagEmoji = "🇷🇺"
    ),

    TURKISH(
        displayName = "Türkçe",
        locale = "tr-TR",
        spaceName = "boşluk",
        row1 = listOf("q","w","e","r","t","y","u","ı","o","p","ğ","ü"),
        row2 = listOf("a","s","d","f","g","h","j","k","l","ş","i"),
        row3 = listOf("z","x","c","v","b","n","m","ö","ç"),
        row1Numbers = listOf("1","2","3","4","5","6","7","8","9","0"),
        row2Numbers = listOf("@","#","₺","%","&","-","+","(",")","\""),
        flagEmoji = "🇹🇷"
    ),

    ARABIC(
        displayName = "العربية",
        locale = "ar-SA",
        spaceName = "مسافة",
        row1 = listOf("ض","ص","ث","ق","ف","غ","ع","ه","خ","ح","ج","د"),
        row2 = listOf("ش","س","ي","ب","ل","ا","ت","ن","م","ك","ط"),
        row3 = listOf("ئ","ء","ؤ","ر","لا","ى","ة","و","ز","ظ"),
        row1Numbers = listOf("١","٢","٣","٤","٥","٦","٧","٨","٩","٠"),
        row2Numbers = listOf("@","#","﷼","%","&","-","+","(",")","\""),
        flagEmoji = "🇸🇦"
    ),

    JAPANESE(
        displayName = "日本語",
        locale = "ja-JP",
        spaceName = "スペース",
        // Romaji layout (standard QWERTY for Japanese romaji input)
        row1 = listOf("q","w","e","r","t","y","u","i","o","p"),
        row2 = listOf("a","s","d","f","g","h","j","k","l"),
        row3 = listOf("z","x","c","v","b","n","m"),
        row1Numbers = listOf("1","2","3","4","5","6","7","8","9","0"),
        row2Numbers = listOf("@","#","¥","%","&","-","+","(",")","\""),
        flagEmoji = "🇯🇵"
    ),

    CHINESE(
        displayName = "中文",
        locale = "zh-CN",
        spaceName = "空格",
        // Pinyin layout (standard QWERTY)
        row1 = listOf("q","w","e","r","t","y","u","i","o","p"),
        row2 = listOf("a","s","d","f","g","h","j","k","l"),
        row3 = listOf("z","x","c","v","b","n","m"),
        row1Numbers = listOf("1","2","3","4","5","6","7","8","9","0"),
        row2Numbers = listOf("@","#","¥","%","&","-","+","(",")","\""),
        flagEmoji = "🇨🇳"
    );

    fun next(): KeyboardLanguage {
        val values = values()
        return values[(ordinal + 1) % values.size]
    }

    companion object {
        fun fromLocale(locale: String): KeyboardLanguage {
            return values().find { it.locale.startsWith(locale.take(2), ignoreCase = true) }
                ?: ENGLISH
        }

        val all: List<KeyboardLanguage> = values().toList()
    }
}
