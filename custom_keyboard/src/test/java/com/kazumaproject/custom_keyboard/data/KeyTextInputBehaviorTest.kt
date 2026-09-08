package com.kazumaproject.custom_keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyTextInputBehaviorTest {
    @Test
    fun missingOrUnknownValue_defaultsToNormal() {
        assertEquals(KeyTextInputBehavior.NORMAL, KeyTextInputBehavior.fromDbValue(null))
        assertEquals(KeyTextInputBehavior.NORMAL, KeyTextInputBehavior.fromDbValue("UNKNOWN"))
    }

    @Test
    fun toggleValue_isRestored() {
        assertEquals(KeyTextInputBehavior.TOGGLE, KeyTextInputBehavior.fromDbValue("TOGGLE"))
    }

    @Test
    fun petalToggleOrder_isTapLeftUpRightDown() {
        assertEquals(
            listOf(
                FlickDirection.TAP,
                FlickDirection.UP_LEFT_FAR,
                FlickDirection.UP,
                FlickDirection.UP_RIGHT_FAR,
                FlickDirection.DOWN
            ),
            PETAL_TOGGLE_DIRECTIONS
        )
    }
}
