package com.kazumaproject.core.domain.flick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class FlickGestureMathTest {

    @Test
    fun sensitivityCurveKeepsNormalAtConfiguredMultiplier() {
        assertEquals(
            73.5f,
            FlickGestureMath.thresholdPxForSensitivity(
                sensitivity = 100,
                scaledTouchSlopPx = 21,
                sensitiveMultiplier = 1.5f,
                normalMultiplier = 3.5f,
                stableMultiplier = 4.25f
            ),
            0.001f
        )
    }

    @Test
    fun sensitivityCurveClampsBothEnds() {
        assertEquals(
            31.5f,
            FlickGestureMath.thresholdPxForSensitivity(
                sensitivity = -10,
                scaledTouchSlopPx = 21,
                sensitiveMultiplier = 1.5f,
                normalMultiplier = 3.5f,
                stableMultiplier = 4.25f
            ),
            0.001f
        )
        assertEquals(
            89.25f,
            FlickGestureMath.thresholdPxForSensitivity(
                sensitivity = 500,
                scaledTouchSlopPx = 21,
                sensitiveMultiplier = 1.5f,
                normalMultiplier = 3.5f,
                stableMultiplier = 4.25f
            ),
            0.001f
        )
    }

    @Test
    fun radialThresholdRequiresSamePathLengthForCardinalAndDiagonalMotion() {
        val threshold = 100f
        val diagonalComponentAtThreshold = threshold / sqrt(2f)

        assertTrue(FlickGestureMath.isThresholdCrossed(100f, 0f, threshold))
        assertTrue(
            FlickGestureMath.isThresholdCrossed(
                diagonalComponentAtThreshold,
                diagonalComponentAtThreshold,
                threshold
            )
        )
        assertFalse(FlickGestureMath.isThresholdCrossed(70f, 70f, threshold))
    }

    @Test
    fun rectangularThresholdKeepsDiagonalMotionInsideUntilEitherAxisCrosses() {
        val threshold = 100f

        assertTrue(
            FlickGestureMath.isThresholdCrossed(
                80f,
                80f,
                threshold,
                FlickThresholdShape.Radial
            )
        )
        assertFalse(
            FlickGestureMath.isThresholdCrossed(
                80f,
                80f,
                threshold,
                FlickThresholdShape.Rectangular
            )
        )
        assertTrue(
            FlickGestureMath.isThresholdCrossed(
                100f,
                20f,
                threshold,
                FlickThresholdShape.Rectangular
            )
        )
    }

    @Test
    fun cardinalDirectionUsesDominantAxisAfterRadialThreshold() {
        assertEquals(
            FlickDirection.Tap,
            FlickGestureMath.cardinalDirection(20f, -20f, 50f)
        )
        assertEquals(
            FlickDirection.Left,
            FlickGestureMath.cardinalDirection(-60f, 10f, 50f)
        )
        assertEquals(
            FlickDirection.Top,
            FlickGestureMath.cardinalDirection(30f, -45f, 50f)
        )
        assertEquals(
            FlickDirection.Right,
            FlickGestureMath.cardinalDirection(60f, 10f, 50f)
        )
        assertEquals(
            FlickDirection.Bottom,
            FlickGestureMath.cardinalDirection(30f, 45f, 50f)
        )
    }

    @Test
    fun cardinalDirectionUsesSelectedThresholdShapeBeforeChoosingDominantAxis() {
        assertEquals(
            FlickDirection.Bottom,
            FlickGestureMath.cardinalDirection(
                deltaX = 80f,
                deltaY = 90f,
                thresholdPx = 100f,
                thresholdShape = FlickThresholdShape.Radial
            )
        )
        assertEquals(
            FlickDirection.Tap,
            FlickGestureMath.cardinalDirection(
                deltaX = 80f,
                deltaY = 90f,
                thresholdPx = 100f,
                thresholdShape = FlickThresholdShape.Rectangular
            )
        )
    }

    @Test
    fun unknownPreferenceValueFallsBackToRadial() {
        assertEquals(
            FlickThresholdShape.Radial,
            FlickThresholdShape.fromPreferenceValue("unknown")
        )
    }
}
