package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuminousBlobSimulationTest {

    @Test
    fun containedBaseRadiusUsesKeyboardShortSide() {
        val wideKeyboardRadius = LuminousBlobSimulation.calculateContainedBaseRadius(
            width = 1080,
            height = 300
        )
        val tallKeyboardRadius = LuminousBlobSimulation.calculateContainedBaseRadius(
            width = 300,
            height = 1080
        )

        assertEquals(wideKeyboardRadius, tallKeyboardRadius, 0.001f)
    }

    @Test
    fun containedBaseRadiusScalesWithKeyboardSize() {
        val compactKeyboardRadius = LuminousBlobSimulation.calculateContainedBaseRadius(
            width = 1080,
            height = 300
        )
        val tallerKeyboardRadius = LuminousBlobSimulation.calculateContainedBaseRadius(
            width = 1080,
            height = 600
        )

        assertTrue(tallerKeyboardRadius > compactKeyboardRadius)
        assertEquals(compactKeyboardRadius * 2f, tallerKeyboardRadius, 0.01f)
    }

    @Test
    fun containedBaseRadiusLeavesRoomForPullAndGlow() {
        val keyboardHeight = 300
        val radius = LuminousBlobSimulation.calculateContainedBaseRadius(
            width = 1080,
            height = keyboardHeight
        )

        assertTrue(radius <= keyboardHeight * 0.28f)
    }

    @Test
    fun stableRadiusIgnoresHeightOnlyResizeFromComposingText() {
        val currentRadius = LuminousBlobSimulation.calculateContainedBaseRadius(
            width = 1080,
            height = 360
        )

        val resolvedRadius = LuminousBlobSimulation.resolveStableBaseRadiusAfterResize(
            currentRadius = currentRadius,
            previousWidth = 1080,
            previousHeight = 360,
            newWidth = 1080,
            newHeight = 300
        )

        assertEquals(currentRadius, resolvedRadius, 0.001f)
    }

    @Test
    fun stableRadiusRecalculatesForStructuralWidthResize() {
        val currentRadius = LuminousBlobSimulation.calculateContainedBaseRadius(
            width = 1080,
            height = 360
        )
        val expectedRadius = LuminousBlobSimulation.calculateContainedBaseRadius(
            width = 300,
            height = 360
        )

        val resolvedRadius = LuminousBlobSimulation.resolveStableBaseRadiusAfterResize(
            currentRadius = currentRadius,
            previousWidth = 1080,
            previousHeight = 360,
            newWidth = 300,
            newHeight = 360
        )

        assertEquals(expectedRadius, resolvedRadius, 0.001f)
        assertTrue(resolvedRadius < currentRadius)
    }

    @Test
    fun stableRadiusRecalculatesForOrientationChange() {
        assertTrue(
            LuminousBlobSimulation.shouldRecalculateStableBaseRadius(
                previousWidth = 1080,
                previousHeight = 360,
                newWidth = 360,
                newHeight = 1080
            )
        )
    }
}
