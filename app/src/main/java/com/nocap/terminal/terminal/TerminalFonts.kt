package com.nocap.terminal.terminal

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Terminal fonts: the bundled programming fonts in assets/fonts, the system monospace font, or a
 * font file imported by the user.
 */
object TerminalFonts {
    private const val TAG = "TerminalFonts"
    private const val ASSET_DIR = "fonts"

    const val SYSTEM = "System monospace"
    const val CUSTOM = "Custom font"
    const val DEFAULT = "JetBrains Mono"

    private val cache = ConcurrentHashMap<String, Typeface>()

    /** Bundled font names, e.g. "JetBrains Mono", sorted. */
    fun bundled(context: Context): List<String> =
        context.assets.list(ASSET_DIR).orEmpty()
            .filter { it.endsWith(".ttf") }
            .map { it.removeSuffix(".ttf").replace('_', ' ') }
            .sortedBy { it.lowercase() }

    /** Every selectable font: bundled ones, system monospace and, if imported, the custom font. */
    fun all(context: Context): List<String> =
        buildList {
            addAll(bundled(context))
            add(SYSTEM)
            if (hasCustomFont(context)) add(CUSTOM)
        }

    private fun fontFile(context: Context) = File(context.filesDir, "fonts/terminal.ttf")

    fun hasCustomFont(context: Context): Boolean = fontFile(context).exists()

    /** The typeface for [name], falling back to the system monospace font. */
    fun load(context: Context, name: String): Typeface {
        if (name == SYSTEM) return Typeface.MONOSPACE
        if (name == CUSTOM) {
            val file = fontFile(context)
            return runCatching { Typeface.createFromFile(file) }
                .onFailure { Log.w(TAG, "Cannot load custom font", it) }
                .getOrDefault(Typeface.MONOSPACE)
        }
        return cache.getOrPut(name) {
            runCatching { Typeface.createFromAsset(context.assets, "$ASSET_DIR/${name.replace(' ', '_')}.ttf") }
                .onFailure { Log.w(TAG, "Cannot load font $name", it) }
                .getOrDefault(Typeface.MONOSPACE)
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

    fun deleteCustomFont(context: Context) {
        fontFile(context).delete()
    }
}
