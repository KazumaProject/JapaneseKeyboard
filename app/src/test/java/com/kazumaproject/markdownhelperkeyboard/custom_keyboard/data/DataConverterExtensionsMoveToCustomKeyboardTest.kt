package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data

import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.CircularFlickDirection
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Test

class DataConverterExtensionsMoveToCustomKeyboardTest {

    @Test
    fun newSpecialActionsRoundTripThroughDbStrings() {
        val cases = listOf(
            KeyAction.ToggleDakutenOnly to "ToggleDakutenOnly",
            KeyAction.ToggleHandakutenOnly to "ToggleHandakutenOnly",
            KeyAction.ForceHalfWidthSpace to "ForceHalfWidthSpace",
            KeyAction.ForceFullWidthSpace to "ForceFullWidthSpace",
            KeyAction.MoveCursorUp to "MOVE_CURSOR_UP",
            KeyAction.MoveCursorDown to "MOVE_CURSOR_DOWN"
        )

        cases.forEach { (action, actionType) ->
            val dbStrings = FlickAction.Action(action).toDbStrings()

            assertEquals(actionType, dbStrings.first)
            assertEquals(null, dbStrings.second)

            val flickRestored = FlickMapping(
                ownerKeyId = 1L,
                stateIndex = 0,
                flickDirection = FlickDirection.TAP,
                actionType = dbStrings.first,
                actionValue = dbStrings.second
            ).toFlickAction()
            assertEquals(FlickAction.Action(action), flickRestored)

            val circularRestored = CircularFlickMapping(
                ownerKeyId = 1L,
                stateIndex = 0,
                circularDirection = CircularFlickDirection.SLOT_0,
                actionType = dbStrings.first,
                actionValue = dbStrings.second
            ).toFlickAction()
            assertEquals(FlickAction.Action(action), circularRestored)
        }
    }

    @Test
    fun moveToCustomKeyboardRoundTripsThroughDbStrings() {
        val stableId = "stable-target"
        val dbStrings = FlickAction.Action(KeyAction.MoveToCustomKeyboard(stableId)).toDbStrings()

        assertEquals("MoveToCustomKeyboard", dbStrings.first)
        assertEquals(stableId, dbStrings.second)

        val restored = FlickMapping(
            ownerKeyId = 1L,
            stateIndex = 0,
            flickDirection = FlickDirection.TAP,
            actionType = dbStrings.first,
            actionValue = dbStrings.second
        ).toFlickAction()

        assertEquals(FlickAction.Action(KeyAction.MoveToCustomKeyboard(stableId)), restored)

        val circularRestored = CircularFlickMapping(
            ownerKeyId = 1L,
            stateIndex = 0,
            circularDirection = CircularFlickDirection.SLOT_0,
            actionType = dbStrings.first,
            actionValue = dbStrings.second
        ).toFlickAction()

        assertEquals(FlickAction.Action(KeyAction.MoveToCustomKeyboard(stableId)), circularRestored)
    }

    @Test
    fun existingMoveCustomKeyboardTabStillRoundTripsThroughDbStrings() {
        val dbStrings = FlickAction.Action(KeyAction.MoveCustomKeyboardTab).toDbStrings()

        assertEquals("MoveCustomKeyboardTab", dbStrings.first)
        assertEquals(CircularFlickSlotActionMapper.SWITCH_MAP_LABEL, dbStrings.second)

        val restored = FlickMapping(
            ownerKeyId = 1L,
            stateIndex = 0,
            flickDirection = FlickDirection.TAP,
            actionType = dbStrings.first,
            actionValue = dbStrings.second
        ).toFlickAction()

        assertEquals(
            FlickAction.Action(
                KeyAction.MoveCustomKeyboardTab,
                label = CircularFlickSlotActionMapper.SWITCH_MAP_LABEL
            ),
            restored
        )
    }
}
