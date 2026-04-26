package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalKeyboardShortcutActionTest {
    @Test
    fun availableFor_returnsCompositionActions() {
        val ids = PhysicalKeyboardShortcutAction.availableFor(PhysicalKeyboardShortcutContext.COMPOSITION).map { it.id }
        assertTrue(ids.contains("convert"))
        assertTrue(ids.contains("convert_to_hiragana"))
        assertFalse(ids.contains("convert_next"))
    }

    @Test
    fun availableFor_returnsConversionActions() {
        val ids = PhysicalKeyboardShortcutAction.availableFor(PhysicalKeyboardShortcutContext.CONVERSION).map { it.id }
        assertTrue(ids.contains("convert_next"))
        assertTrue(ids.contains("cancel"))
        assertFalse(ids.contains("convert_to_hiragana"))
    }

    @Test
    fun actionList_doesNotContainSwitchKanaType() {
        assertFalse(PhysicalKeyboardShortcutAction.entries.any { it.id == "switch_kana_type" })
    }
}
