package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data

import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Test

class DataConverterExtensionsMoveToCustomKeyboardTest {

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
