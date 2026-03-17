package com.voxtype.keyboard.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * VibrationManager – provides haptic feedback for key presses and special events.
 * Handles both the legacy Vibrator API (< API 31) and VibratorManager (>= API 31).
 */
class VibrationManager(context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val prefs = context.getSharedPreferences("voxtype_settings", Context.MODE_PRIVATE)

    private val isEnabled: Boolean
        get() = prefs.getBoolean("vibration_enabled", true)

    // ─── Feedback types ───────────────────────────────────────────────────────

    /** Short click for regular character keys (28 ms). */
    fun keyFeedback() {
        if (!isEnabled) return
        vibrate(28)
    }

    /** Slightly stronger tap for special keys (delete, shift, enter) – 40 ms. */
    fun specialKeyFeedback() {
        if (!isEnabled) return
        vibrate(40)
    }

    /** Double pulse for mic button press. */
    fun micFeedback() {
        if (!isEnabled) return
        vibratePattern(longArrayOf(0, 30, 60, 30))
    }

    /** Long press confirmation – 60 ms. */
    fun longPressFeedback() {
        if (!isEnabled) return
        vibrate(60)
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun vibrate(durationMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private fun vibratePattern(pattern: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
}
