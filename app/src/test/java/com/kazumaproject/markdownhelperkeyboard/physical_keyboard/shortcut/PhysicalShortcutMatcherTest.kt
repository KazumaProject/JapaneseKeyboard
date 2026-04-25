package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut

import android.view.KeyEvent
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.database.PhysicalKeyboardShortcutItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhysicalShortcutMatcherTest {
    @Test
    fun match_prefersCurrentContextOverAny() {
        val any = PhysicalKeyboardShortcutItem(context = "any", keyCode = KeyEvent.KEYCODE_SPACE, actionId = "cycle_input_mode")
        val conversion = PhysicalKeyboardShortcutItem(context = "conversion", keyCode = KeyEvent.KEYCODE_SPACE, actionId = "convert_next")
        val matched = PhysicalShortcutMatcher.match(
            listOf(any, conversion),
            PhysicalKeyboardShortcutContext.CONVERSION,
            KeyEvent.KEYCODE_SPACE,
            scanCode = 0,
            ctrl = false,
            shift = false,
            alt = false,
            meta = false
        )
        assertEquals("convert_next", matched?.actionId)
    }

    @Test
    fun match_fallsBackToAny() {
        val any = PhysicalKeyboardShortcutItem(context = "any", keyCode = KeyEvent.KEYCODE_C, ctrl = true, actionId = "copy")
        assertEquals(
            any,
            PhysicalShortcutMatcher.match(
                listOf(any),
                PhysicalKeyboardShortcutContext.COMPOSITION,
                KeyEvent.KEYCODE_C,
                scanCode = 0,
                ctrl = true,
                shift = false,
                alt = false,
                meta = false
            )
        )
    }

    @Test
    fun match_matchesShiftLeft() {
        val item = PhysicalKeyboardShortcutItem(context = "bunsetsu_conversion", keyCode = KeyEvent.KEYCODE_DPAD_LEFT, shift = true, actionId = "segment_width_shrink")
        assertEquals(
            item,
            PhysicalShortcutMatcher.match(
                listOf(item),
                PhysicalKeyboardShortcutContext.BUNSETSU_CONVERSION,
                KeyEvent.KEYCODE_DPAD_LEFT,
                scanCode = 0,
                ctrl = false,
                shift = true,
                alt = false,
                meta = false
            )
        )
    }

    @Test
    fun match_rejectsDisabledScanCodeMetaAndWrongContext() {
        val disabled = PhysicalKeyboardShortcutItem(context = "any", keyCode = KeyEvent.KEYCODE_C, ctrl = true, actionId = "copy", enabled = false)
        val scan = PhysicalKeyboardShortcutItem(context = "any", keyCode = KeyEvent.KEYCODE_C, scanCode = 10, ctrl = true, actionId = "copy")
        val meta = PhysicalKeyboardShortcutItem(context = "any", keyCode = KeyEvent.KEYCODE_C, meta = true, actionId = "copy")
        val composition = PhysicalKeyboardShortcutItem(context = "composition", keyCode = KeyEvent.KEYCODE_C, actionId = "commit")
        assertNull(PhysicalShortcutMatcher.match(listOf(disabled), PhysicalKeyboardShortcutContext.ANY, KeyEvent.KEYCODE_C, 0, true, false, false, false))
        assertNull(PhysicalShortcutMatcher.match(listOf(scan), PhysicalKeyboardShortcutContext.ANY, KeyEvent.KEYCODE_C, 11, true, false, false, false))
        assertNull(PhysicalShortcutMatcher.match(listOf(meta), PhysicalKeyboardShortcutContext.ANY, KeyEvent.KEYCODE_C, 0, false, false, false, false))
        assertNull(PhysicalShortcutMatcher.match(listOf(composition), PhysicalKeyboardShortcutContext.CONVERSION, KeyEvent.KEYCODE_C, 0, false, false, false, false))
    }
}
