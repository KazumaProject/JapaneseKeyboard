package com.kazumaproject.custom_keyboard.controller

import com.kazumaproject.core.data.popup.TfbiFlickStartPositionMode
import com.kazumaproject.custom_keyboard.data.TfbiGuideFingerPosition
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class TfbiGuideFingerPositionTest {

    @Test
    fun touchPointModePreservesTheActualKeyLocalPosition() {
        assertEquals(
            TfbiGuideFingerPosition(x = 0.1f, y = 0.8f),
            resolveTfbiGuideFingerPosition(
                keyWidth = 200,
                keyHeight = 100,
                eventX = 20f,
                eventY = 80f,
                startPositionMode = TfbiFlickStartPositionMode.TOUCH_POINT,
                downX = 20f,
                downY = 80f
            )
        )
    }

    @Test
    fun keyCenterModeStartsAtTheCenterRegardlessOfTouchPoint() {
        assertEquals(
            TfbiGuideFingerPosition(x = 0.5f, y = 0.5f),
            resolveTfbiGuideFingerPosition(
                keyWidth = 200,
                keyHeight = 100,
                eventX = 180f,
                eventY = 80f,
                startPositionMode = TfbiFlickStartPositionMode.KEY_CENTER,
                downX = 180f,
                downY = 80f
            )
        )
    }

    @Test
    fun keyCenterModeFollowsDisplacementAndClampsAtTheKeyBounds() {
        assertEquals(
            TfbiGuideFingerPosition(x = 0.6f, y = 0.2f),
            resolveTfbiGuideFingerPosition(
                keyWidth = 200,
                keyHeight = 100,
                eventX = 200f,
                eventY = 50f,
                startPositionMode = TfbiFlickStartPositionMode.KEY_CENTER,
                downX = 180f,
                downY = 80f
            )
        )
        assertEquals(
            TfbiGuideFingerPosition(x = 0f, y = 1f),
            resolveTfbiGuideFingerPosition(
                keyWidth = 200,
                keyHeight = 100,
                eventX = -100f,
                eventY = 130f,
                startPositionMode = TfbiFlickStartPositionMode.KEY_CENTER,
                downX = 180f,
                downY = 80f
            )
        )
    }

    @Test
    fun centeredGuidePositionKeepsTheCenterCellAsTapBeforeMovement() {
        val position = resolveTfbiGuideFingerPosition(
            keyWidth = 200,
            keyHeight = 100,
            eventX = 180f,
            eventY = 80f,
            startPositionMode = TfbiFlickStartPositionMode.KEY_CENTER,
            downX = 180f,
            downY = 80f
        )

        assertEquals(
            TfbiFlickDirection.TAP,
            resolveTfbiGuideGridDirection(
                position = position!!,
                enabledDirections = TfbiFlickDirection.entries.toSet()
            )
        )
    }
}
