package com.kazumaproject.custom_keyboard

import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.SumireSpecialKeyDirection
import com.kazumaproject.custom_keyboard.data.toSumireSpecialKeyDirectionOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

class FlickKeyboardViewSumireSpecialKeyDirectionTest {
    @Test
    fun mapsRuntimeFlickDirectionToSumireSpecialKeyDirection() {
        assertEquals(
            SumireSpecialKeyDirection.TAP,
            FlickDirection.TAP.toSumireSpecialKeyDirectionOrNull()
        )
        assertEquals(
            SumireSpecialKeyDirection.UP,
            FlickDirection.UP.toSumireSpecialKeyDirectionOrNull()
        )
        assertEquals(
            SumireSpecialKeyDirection.DOWN,
            FlickDirection.DOWN.toSumireSpecialKeyDirectionOrNull()
        )
        assertEquals(
            SumireSpecialKeyDirection.LEFT,
            FlickDirection.UP_LEFT.toSumireSpecialKeyDirectionOrNull()
        )
        assertEquals(
            SumireSpecialKeyDirection.LEFT,
            FlickDirection.UP_LEFT_FAR.toSumireSpecialKeyDirectionOrNull()
        )
        assertEquals(
            SumireSpecialKeyDirection.RIGHT,
            FlickDirection.UP_RIGHT.toSumireSpecialKeyDirectionOrNull()
        )
        assertEquals(
            SumireSpecialKeyDirection.RIGHT,
            FlickDirection.UP_RIGHT_FAR.toSumireSpecialKeyDirectionOrNull()
        )
    }
}

