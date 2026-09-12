package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import org.junit.Assert.*
import org.junit.Test

class ComposingGuidePlacementTest {
    @Test fun migrationKeepsTopPositionWhenTheNewTextRowsNeedMoreHeight() {
        val oldArea = GuideBounds(0, 24, 1000, 900)
        val newArea = GuideBounds(0, 24, 1000, 1700)
        val saved = ComposingGuidePlacement(.2f, 1f, 280f, 232f)
        val old = saved.resolve(oldArea.x, oldArea.y, oldArea.width, oldArea.height, 2f)
        val migrated = saved.rebase(oldArea, newArea, 2f, 300f)
            .resolve(newArea.x, newArea.y, newArea.width, newArea.height, 2f)
        assertEquals(old.x, migrated.x)
        assertEquals(old.y, migrated.y)
        assertEquals(600, migrated.height)
    }

    @Test fun initialPlacementIsCenteredAboveKeyboard() {
        assertEquals(GuideBounds(220, 216, 560, 528), ComposingGuidePlacement().resolve(0, 24, 1000, 720, 2f))
    }

    @Test fun rebasingPreservesScreenPositionAndAllowsMovingBelowTheOldKeyboardLimit() {
        val oldArea = GuideBounds(0, 24, 1000, 900)
        val newArea = GuideBounds(0, 24, 1000, 1700)
        val saved = ComposingGuidePlacement(.2f, .8f, 280f, 264f)
        val original = saved.resolve(oldArea.x, oldArea.y, oldArea.width, oldArea.height, 2f)
        val migrated = saved.rebase(oldArea, newArea, 2f)
        assertEquals(original, migrated.resolve(newArea.x, newArea.y, newArea.width, newArea.height, 2f))
        val moved = migrated.copy(yFraction = 1f).resolve(newArea.x, newArea.y, newArea.width, newArea.height, 2f)
        assertTrue(moved.y > oldArea.bottom)
        assertEquals(newArea.bottom, moved.bottom)
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
        assertEquals(GuideBounds(10, 624, 160, 96), result)
    }
}
