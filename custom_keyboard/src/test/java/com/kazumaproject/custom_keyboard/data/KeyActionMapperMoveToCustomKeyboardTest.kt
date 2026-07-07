package com.kazumaproject.custom_keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyActionMapperMoveToCustomKeyboardTest {

    @Test
    fun moveToCustomKeyboardRoundTripsThroughString() {
        val stableId = "stable-target"

        val saved = KeyActionMapper.fromKeyAction(KeyAction.MoveToCustomKeyboard(stableId))

        assertEquals("MoveToCustomKeyboard:$stableId", saved)
        assertEquals(KeyAction.MoveToCustomKeyboard(stableId), KeyActionMapper.toKeyAction(saved))
    }

    @Test
    fun blankMoveToCustomKeyboardIsNotSerializedOrRestored() {
        assertNull(KeyActionMapper.fromKeyAction(KeyAction.MoveToCustomKeyboard("")))
        assertNull(KeyActionMapper.toKeyAction("MoveToCustomKeyboard:"))
    }

    @Test
    fun existingActionsKeepTheirStringFormat() {
        assertEquals("MoveCustomKeyboardTab", KeyActionMapper.fromKeyAction(KeyAction.MoveCustomKeyboardTab))
        assertEquals(KeyAction.MoveCustomKeyboardTab, KeyActionMapper.toKeyAction("MoveCustomKeyboardTab"))
        assertEquals("^_^", KeyActionMapper.fromKeyAction(KeyAction.ShowEmojiKeyboard))
        assertEquals(KeyAction.ShowEmojiKeyboard, KeyActionMapper.toKeyAction("^_^"))
        assertEquals("小゛゜", KeyActionMapper.fromKeyAction(KeyAction.ToggleDakuten))
        assertEquals(KeyAction.ToggleDakuten, KeyActionMapper.toKeyAction("小゛゜"))
        assertEquals("a/A", KeyActionMapper.fromKeyAction(KeyAction.ToggleCase))
        assertEquals(KeyAction.ToggleCase, KeyActionMapper.toKeyAction("a/A"))
    }
}
