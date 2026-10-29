package com.vibez.terminal.terminal

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import java.io.File

/** The terminal font: the system monospace font, or a font file imported by the user. */
object TerminalFonts {
    private const val TAG = "TerminalFonts"

    private fun fontFile(context: Context) = File(context.filesDir, "fonts/terminal.ttf")

    fun hasCustomFont(context: Context): Boolean = fontFile(context).exists()

    fun load(context: Context): Typeface {
        val file = fontFile(context)
        if (!file.exists()) return Typeface.MONOSPACE
        return try {
            Typeface.createFromFile(file)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot load custom font, using monospace", e)
            Typeface.MONOSPACE
        }
    }

    /** Copy a TrueType/OpenType font into app storage. Throws if it is not a usable font. */
    fun import(context: Context, uri: Uri) {
        val target = fontFile(context)
        val temporary = File(target.parentFile, "import.tmp")
        target.parentFile?.mkdirs()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { input.copyTo(it) }
            } ?: throw IllegalArgumentException("Cannot open the selected file")
            // Validate before replacing the current font.
            Typeface.createFromFile(temporary)
            if (!temporary.renameTo(target)) throw IllegalStateException("Cannot save the font")
        } catch (e: RuntimeException) {
            throw IllegalArgumentException("Not a valid font file", e)
        } finally {
            temporary.delete()
        }
    }

    fun reset(context: Context) {
        fontFile(context).delete()
    }
}
