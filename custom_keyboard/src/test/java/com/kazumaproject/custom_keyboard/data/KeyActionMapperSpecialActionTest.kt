package com.kazumaproject.custom_keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyActionMapperSpecialActionTest {

    @Test
    fun newSpecialActionsRoundTripThroughStableStrings() {
        val cases = listOf(
            KeyAction.ToggleDakutenOnly to "ToggleDakutenOnly",
            KeyAction.ToggleHandakutenOnly to "ToggleHandakutenOnly",
            KeyAction.ForceHalfWidthSpace to "ForceHalfWidthSpace",
            KeyAction.ForceFullWidthSpace to "ForceFullWidthSpace",
            KeyAction.MoveCursorUp to "MoveCursorUp",
            KeyAction.MoveCursorDown to "MoveCursorDown",
            KeyAction.DoNothing to "DoNothing"
        )

        cases.forEach { (action, saved) ->
            assertEquals(saved, KeyActionMapper.fromKeyAction(action))
            assertEquals(action, KeyActionMapper.toKeyAction(saved))
        }
    }

    @Test
    fun existingToggleDakutenCompatibilityStringIsPreserved() {
        assertEquals("小゛゜", KeyActionMapper.fromKeyAction(KeyAction.ToggleDakuten))
        assertEquals(KeyAction.ToggleDakuten, KeyActionMapper.toKeyAction("小゛゜"))
    }
}
