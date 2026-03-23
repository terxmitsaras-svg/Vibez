package com.vibez.keyboard.utils

import android.content.Context
import android.view.inputmethod.InputMethodManager

object KeyboardUtils {

    /**
     * Check if Vortext is enabled as an input method.
     */
    fun isKeyboardEnabled(context: Context): Boolean {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        return imm?.enabledInputMethodList?.any {
            it.packageName == context.packageName
        } ?: false
    }

    /**
     * Check if Vortext is the default keyboard.
     */
    fun isKeyboardDefault(context: Context): Boolean {
        val defaultIme = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
        )
        return defaultIme?.startsWith(context.packageName) == true
    }

    /**
     * Check if microphone permission is granted.
     */
    fun hasMicrophonePermission(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * Convert dp to pixels.
     */
    fun dpToPx(context: Context, dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }

    /**
     * Convert sp to pixels.
     */
    fun spToPx(context: Context, sp: Float): Float {
        return sp * context.resources.displayMetrics.scaledDensity
    }
}
