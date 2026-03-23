package com.vibez.keyboard.theme

import android.graphics.Color

/**
 * Keyboard theme definitions for light and dark modes.
 */
enum class KeyboardTheme(
    val keyboardBackground: Int,
    val keyBackground: Int,
    val specialKeyBackground: Int,
    val pressedKeyBackground: Int,
    val keyTextColor: Int,
    val specialKeyTextColor: Int,
    val altTextColor: Int,
    val accentColor: Int,
    val keyTextSize: Float
) {
    DARK(
        keyboardBackground = Color.parseColor("#1A1A2E"),
        keyBackground = Color.parseColor("#2D2D44"),
        specialKeyBackground = Color.parseColor("#16213E"),
        pressedKeyBackground = Color.parseColor("#4A4A6A"),
        keyTextColor = Color.parseColor("#FFFFFF"),
        specialKeyTextColor = Color.parseColor("#B0B0C8"),
        altTextColor = Color.parseColor("#6B6B8A"),
        accentColor = Color.parseColor("#7B61FF"),
        keyTextSize = 18f
    ),

    LIGHT(
        keyboardBackground = Color.parseColor("#D1D5DB"),
        keyBackground = Color.parseColor("#FFFFFF"),
        specialKeyBackground = Color.parseColor("#ADB5BD"),
        pressedKeyBackground = Color.parseColor("#9CA3AF"),
        keyTextColor = Color.parseColor("#1F2937"),
        specialKeyTextColor = Color.parseColor("#374151"),
        altTextColor = Color.parseColor("#9CA3AF"),
        accentColor = Color.parseColor("#5B21B6"),
        keyTextSize = 18f
    ),

    MIDNIGHT(
        keyboardBackground = Color.parseColor("#0D0D0D"),
        keyBackground = Color.parseColor("#1A1A1A"),
        specialKeyBackground = Color.parseColor("#0A0A0A"),
        pressedKeyBackground = Color.parseColor("#2A2A2A"),
        keyTextColor = Color.parseColor("#E5E5E5"),
        specialKeyTextColor = Color.parseColor("#888888"),
        altTextColor = Color.parseColor("#444444"),
        accentColor = Color.parseColor("#00D4FF"),
        keyTextSize = 18f
    ),

    OCEAN(
        keyboardBackground = Color.parseColor("#0A2342"),
        keyBackground = Color.parseColor("#1B4F8A"),
        specialKeyBackground = Color.parseColor("#0D2B4F"),
        pressedKeyBackground = Color.parseColor("#2563AB"),
        keyTextColor = Color.parseColor("#E0F2FE"),
        specialKeyTextColor = Color.parseColor("#7DD3FC"),
        altTextColor = Color.parseColor("#3B82F6"),
        accentColor = Color.parseColor("#38BDF8"),
        keyTextSize = 18f
    );

    companion object {
        fun fromSystemDarkMode(isDark: Boolean): KeyboardTheme =
            if (isDark) DARK else LIGHT
    }
}
