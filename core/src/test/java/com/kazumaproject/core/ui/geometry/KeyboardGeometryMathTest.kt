package com.kazumaproject.core.ui.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardGeometryMathTest {
    @Test fun curveIsFlatAtZeroAndMirrorsTheTangent() {
        assertEquals(KeyboardCurvePoint(0f, 0f), KeyboardGeometryMath.curve(50f, 100f, 0f, false))
        val right = KeyboardGeometryMath.curve(100f, 100f, 25f, false)
        val left = KeyboardGeometryMath.curve(100f, 100f, 25f, true)
        assertEquals(25f, right.rotationDegrees, 0.001f)
        assertEquals(-25f, left.rotationDegrees, 0.001f)
        assertEquals(right.offsetY, left.offsetY, 0.001f)
        assertTrue(right.offsetY > 0f)
    }

    @Test fun splitGapCannotConsumeTheLetterKeys() {
        assertEquals(0f, KeyboardGeometryMath.effectiveSplitGap(200f, 40, 24f), 0.001f)
        assertEquals(120f, KeyboardGeometryMath.effectiveSplitGap(360f, 40, 24f), 0.001f)
        assertEquals(72f, KeyboardGeometryMath.effectiveSplitGap(360f, 20, 24f), 0.001f)
    }

    @Test fun movementAlongRotatedKeyUpIsStillUp() {
        val (dx, dy) = KeyboardGeometryMath.localDelta(10f, -17.3205f, 30f)
        assertEquals(0f, dx, 0.001f)
        assertEquals(-20f, dy, 0.001f)
    }
}
