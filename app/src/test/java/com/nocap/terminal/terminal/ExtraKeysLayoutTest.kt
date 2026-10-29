package com.nocap.terminal.terminal

import android.view.KeyEvent
import com.nocap.terminal.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtraKeysLayoutTest {

    @Test
    fun defaultLayoutHasTwoRowsOfSevenKeys() {
        val rows = ExtraKeysLayout.parse(AppSettings.DEFAULT_EXTRA_KEYS)
        assertEquals(listOf(7, 7), rows.map { it.size })
        val ctrl = rows[1][1] as ExtraKey.ModifierKey
        assertEquals(Modifier.CTRL, ctrl.modifier)
        val up = rows[0][4] as ExtraKey.Code
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, up.keyCode)
        assertTrue(up.repeatable)
    }

    @Test
    fun unknownTokensAreTypedAsText() {
        val rows = ExtraKeysLayout.parse("  ~  |  f5   paste \n\n  :q  ")
        val first = rows[0]
        assertEquals("~", (first[0] as ExtraKey.Text).text)
        assertEquals("|", (first[1] as ExtraKey.Text).text)
        assertEquals(KeyEvent.KEYCODE_F5, (first[2] as ExtraKey.Code).keyCode)
        assertEquals(ExtraKeyAction.PASTE, (first[3] as ExtraKey.Action).action)
        assertEquals(":q", (rows[1][0] as ExtraKey.Text).text)
        assertEquals(2, rows.size)
    }
}
