package com.kazumaproject.custom_keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CircularFlickDirectionTest {

    @Test
    fun slots_returnsRequestedSlotCountWithoutTap() {
        listOf(4, 5, 6, 7).forEach { count ->
            val slots = CircularFlickDirection.slots(count)

            assertEquals(count, slots.size)
            assertFalse(slots.contains(CircularFlickDirection.TAP))
        }
    }

    @Test
    fun buildEvenCircularRanges_returnsRangeForEachSlot() {
        listOf(4, 5, 6, 7).forEach { count ->
            val ranges = buildEvenCircularRanges(count)

            assertEquals(CircularFlickDirection.slots(count).toSet(), ranges.keys)
        }
    }

    @Test
    fun getDirectionForAngle_handlesRangeThatCrossesZeroDegrees() {
        val ranges = mapOf(
            CircularFlickDirection.SLOT_0 to (315f to 90f)
        )

        assertEquals(
            CircularFlickDirection.SLOT_0,
            getDirectionForAngle(350f, ranges)
        )
        assertEquals(
            CircularFlickDirection.SLOT_0,
            getDirectionForAngle(10f, ranges)
        )
        assertEquals(
            CircularFlickDirection.TAP,
            getDirectionForAngle(180f, ranges)
        )
    }
}
