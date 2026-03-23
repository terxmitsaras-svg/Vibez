package com.vibez.keyboard

import android.app.Application
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat

/**
 * VibezApplication — Application class.
 * Initializes EmojiCompat for proper emoji rendering.
 */
class VibezApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize EmojiCompat with bundled fonts for consistent emoji rendering
        val config = BundledEmojiCompatConfig(this)
        EmojiCompat.init(config)
    }
}
