package com.kazumaproject.custom_keyboard.controller

import com.kazumaproject.custom_keyboard.data.TfbiGuideFingerPosition
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class TfbiGuideGestureResolverTest {

    @Test
    fun everyGuideCellResolvesToItsDirection() {
        val positions = mapOf(
            TfbiFlickDirection.UP_LEFT to TfbiGuideFingerPosition(0.1f, 0.1f),
            TfbiFlickDirection.UP to TfbiGuideFingerPosition(0.5f, 0.1f),
            TfbiFlickDirection.UP_RIGHT to TfbiGuideFingerPosition(0.9f, 0.1f),
            TfbiFlickDirection.LEFT to TfbiGuideFingerPosition(0.1f, 0.5f),
            TfbiFlickDirection.TAP to TfbiGuideFingerPosition(0.5f, 0.5f),
            TfbiFlickDirection.RIGHT to TfbiGuideFingerPosition(0.9f, 0.5f),
            TfbiFlickDirection.DOWN_LEFT to TfbiGuideFingerPosition(0.1f, 0.9f),
            TfbiFlickDirection.DOWN to TfbiGuideFingerPosition(0.5f, 0.9f),
            TfbiFlickDirection.DOWN_RIGHT to TfbiGuideFingerPosition(0.9f, 0.9f)
        )

        positions.forEach { (direction, position) ->
            assertEquals(
                direction,
                resolveTfbiGuideGridDirection(
                    position = position,
                    enabledDirections = TfbiFlickDirection.entries.toSet()
                )
            )
        }
    }

    @Test
    fun positionInBottomCenterCellResolvesToDown() {
        // This is the real-device reproduction: the angle-based resolver used to return
        // DOWN_LEFT here even though the guide marker is in the bottom-center cell.
        val position = TfbiGuideFingerPosition(x = 80f / 212f, y = 110f / 134f)

        assertEquals(
            TfbiFlickDirection.DOWN,
            resolveTfbiGuideGridDirection(
                position = position,
                enabledDirections = setOf(
                    TfbiFlickDirection.DOWN,
                    TfbiFlickDirection.DOWN_LEFT
                )
            )
        )
    }

    @Test
    fun positionAtLeftEdgeResolvesToLeft() {
        assertEquals(
            TfbiFlickDirection.LEFT,
            resolveTfbiGuideGridDirection(
                position = TfbiGuideFingerPosition(x = 0f, y = 0.5f),
                enabledDirections = setOf(TfbiFlickDirection.LEFT)
            )
        )
    }

    @Test
    fun centerCellRemainsTapWhenTapIsNotAnEnabledMapEntry() {
        assertEquals(
            TfbiFlickDirection.TAP,
            resolveTfbiGuideGridDirection(
                position = TfbiGuideFingerPosition(x = 0.5f, y = 0.5f),
                enabledDirections = setOf(TfbiFlickDirection.DOWN)
            )
        )
    }
}
