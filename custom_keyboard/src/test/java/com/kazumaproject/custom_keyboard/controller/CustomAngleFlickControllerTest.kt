package com.kazumaproject.custom_keyboard.controller

import com.kazumaproject.custom_keyboard.data.CircularFlickDirection
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.buildEvenCircularRanges
import com.kazumaproject.custom_keyboard.data.getDirectionForAngle
import com.kazumaproject.custom_keyboard.data.normalizeAngle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomAngleFlickControllerTest {

    @Test
    fun buildEffectiveCircularFlickRanges_compactsSevenDirectionMapByEffectiveActions() {
        assertEffectiveKeys(
            configuredDirectionCount = 7,
            optionalDirections = emptySet(),
            expectedDirections = listOf(
                CircularFlickDirection.SLOT_0,
                CircularFlickDirection.SLOT_1,
                CircularFlickDirection.SLOT_2,
                CircularFlickDirection.SLOT_3
            )
        )
        assertEffectiveKeys(
            configuredDirectionCount = 7,
            optionalDirections = setOf(CircularFlickDirection.SLOT_4),
            expectedDirections = listOf(
                CircularFlickDirection.SLOT_0,
                CircularFlickDirection.SLOT_1,
                CircularFlickDirection.SLOT_2,
                CircularFlickDirection.SLOT_3,
                CircularFlickDirection.SLOT_4
            )
        )
        assertEffectiveKeys(
            configuredDirectionCount = 7,
            optionalDirections = setOf(
                CircularFlickDirection.SLOT_4,
                CircularFlickDirection.SLOT_5
            ),
            expectedDirections = listOf(
                CircularFlickDirection.SLOT_0,
                CircularFlickDirection.SLOT_1,
                CircularFlickDirection.SLOT_2,
                CircularFlickDirection.SLOT_3,
                CircularFlickDirection.SLOT_4,
                CircularFlickDirection.SLOT_5
            )
        )
        assertEffectiveKeys(
            configuredDirectionCount = 7,
            optionalDirections = setOf(
                CircularFlickDirection.SLOT_4,
                CircularFlickDirection.SLOT_5,
                CircularFlickDirection.SLOT_6
            ),
            expectedDirections = listOf(
                CircularFlickDirection.SLOT_0,
                CircularFlickDirection.SLOT_1,
                CircularFlickDirection.SLOT_2,
                CircularFlickDirection.SLOT_3,
                CircularFlickDirection.SLOT_4,
                CircularFlickDirection.SLOT_5,
                CircularFlickDirection.SLOT_6
            )
        )
    }

    @Test
    fun buildEffectiveCircularFlickRanges_compactsGappedOptionalDirections() {
        assertEffectiveKeys(
            configuredDirectionCount = 7,
            optionalDirections = setOf(CircularFlickDirection.SLOT_5),
            expectedDirections = listOf(
                CircularFlickDirection.SLOT_0,
                CircularFlickDirection.SLOT_1,
                CircularFlickDirection.SLOT_2,
                CircularFlickDirection.SLOT_3,
                CircularFlickDirection.SLOT_5
            )
        )
        assertEffectiveKeys(
            configuredDirectionCount = 7,
            optionalDirections = setOf(
                CircularFlickDirection.SLOT_5,
                CircularFlickDirection.SLOT_6
            ),
            expectedDirections = listOf(
                CircularFlickDirection.SLOT_0,
                CircularFlickDirection.SLOT_1,
                CircularFlickDirection.SLOT_2,
                CircularFlickDirection.SLOT_3,
                CircularFlickDirection.SLOT_5,
                CircularFlickDirection.SLOT_6
            )
        )
    }

    @Test
    fun buildEffectiveCircularFlickRanges_compactsSixDirectionMapAndIgnoresSlotSix() {
        assertEffectiveKeys(
            configuredDirectionCount = 6,
            optionalDirections = setOf(CircularFlickDirection.SLOT_4),
            expectedDirections = listOf(
                CircularFlickDirection.SLOT_0,
                CircularFlickDirection.SLOT_1,
                CircularFlickDirection.SLOT_2,
                CircularFlickDirection.SLOT_3,
                CircularFlickDirection.SLOT_4
            )
        )
        assertEffectiveKeys(
            configuredDirectionCount = 6,
            optionalDirections = setOf(
                CircularFlickDirection.SLOT_4,
                CircularFlickDirection.SLOT_5,
                CircularFlickDirection.SLOT_6
            ),
            expectedDirections = listOf(
                CircularFlickDirection.SLOT_0,
                CircularFlickDirection.SLOT_1,
                CircularFlickDirection.SLOT_2,
                CircularFlickDirection.SLOT_3,
                CircularFlickDirection.SLOT_4,
                CircularFlickDirection.SLOT_5
            )
        )
    }

    @Test
    fun buildEffectiveCircularFlickRanges_reusesCompactRangesForActualDirections() {
        val ranges = buildEffectiveCircularFlickRanges(
            configuredDirectionCount = 7,
            map = mapWithOptionalDirections(CircularFlickDirection.SLOT_5)
        )
        val compactSlotFourRange = buildEvenCircularRanges(5)
            .getValue(CircularFlickDirection.SLOT_4)
        val slotFiveRange = ranges.getValue(CircularFlickDirection.SLOT_5)
        val slotFiveMidAngle = normalizeAngle(slotFiveRange.first + slotFiveRange.second / 2f)

        assertEquals(compactSlotFourRange, slotFiveRange)
        assertEquals(CircularFlickDirection.SLOT_5, getDirectionForAngle(slotFiveMidAngle, ranges))
    }

    @Test
    fun buildEffectiveCircularFlickRanges_preservesConfiguredRangesWhenEverySlotIsActive() {
        val configuredRanges = buildEvenCircularRanges(7).mapValues { (_, range) ->
            range.first + 3f to range.second
        }
        val ranges = buildEffectiveCircularFlickRanges(
            configuredDirectionCount = 7,
            map = mapWithOptionalDirections(
                CircularFlickDirection.SLOT_4,
                CircularFlickDirection.SLOT_5,
                CircularFlickDirection.SLOT_6
            ),
            configuredRanges = configuredRanges
        )

        assertEquals(configuredRanges, ranges)
    }

    @Test
    fun hasEffectiveCircularFlickAction_matchesInputAndActionRules() {
        assertFalse(hasEffectiveCircularFlickAction(null))
        assertFalse(hasEffectiveCircularFlickAction(FlickAction.Input("")))
        assertTrue(hasEffectiveCircularFlickAction(FlickAction.Input("", label = "表示")))
        assertTrue(hasEffectiveCircularFlickAction(FlickAction.Input("x")))
        assertTrue(
            hasEffectiveCircularFlickAction(
                FlickAction.Action(KeyAction.MoveCustomKeyboardTab, label = "⇄")
            )
        )
    }

    private fun assertEffectiveKeys(
        configuredDirectionCount: Int,
        optionalDirections: Set<CircularFlickDirection>,
        expectedDirections: List<CircularFlickDirection>
    ) {
        val ranges = buildEffectiveCircularFlickRanges(
            configuredDirectionCount = configuredDirectionCount,
            map = mapWithOptionalDirections(*optionalDirections.toTypedArray())
        )

        assertEquals(expectedDirections, ranges.keys.toList())
        assertEquals(360f / expectedDirections.size, ranges.values.first().second)
    }

    private fun mapWithOptionalDirections(
        vararg directions: CircularFlickDirection
    ): Map<CircularFlickDirection, FlickAction> {
        return directions.associateWith { FlickAction.Input(it.name, label = it.name) }
    }
}
