package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import org.junit.Assert.*
import org.junit.Test

class ComposingGuidePlacementTest {
    @Test fun initialPlacementIsCenteredAboveKeyboard() {
        assertEquals(GuideBounds(220, 216, 560, 528), ComposingGuidePlacement().resolve(0, 24, 1000, 720, 2f))
    }

    @Test fun splitScreenClampsWithoutChangingSavedPlacement() {
        val saved = ComposingGuidePlacement(.8f, .3f, 500f, 400f)
        val compact = saved.resolve(200, 24, 300, 200, 2f)
        assertEquals(GuideBounds(200, 24, 300, 200), compact)
        val restored = saved.resolve(0, 24, 1600, 1200, 2f)
        assertEquals(1000, restored.width)
        assertEquals(800, restored.height)
        assertEquals(480, restored.x)
    }

    @Test fun outOfRangePositionAndDimensionsStayInsideAvailableArea() {
        val result = ComposingGuidePlacement(-1f, 3f, 1f, 1f).resolve(10, 20, 600, 700, 1f)
        assertEquals(GuideBounds(10, 624, 200, 96), result)
    }
}
