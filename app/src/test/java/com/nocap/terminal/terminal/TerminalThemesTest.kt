package com.nocap.terminal.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalThemesTest {
    @Test
    fun themesAreCompleteAndUnique() {
        val themes = TerminalThemes.all
        assertTrue(themes.size >= 40)
        assertEquals(themes.size, themes.map { it.name }.toSet().size)
        themes.forEach { theme ->
            val colors = theme.ansi.toList() + theme.background + theme.foreground + theme.cursor
            assertTrue("${theme.name} has a transparent color", colors.all { it ushr 24 == 0xFF })
            assertTrue("${theme.name}: foreground equals background", theme.foreground != theme.background)
        }
        assertEquals("No Cap Night", TerminalThemes.default.name)
        assertEquals(TerminalThemes.default, TerminalThemes.byName("does not exist"))
    }
}
