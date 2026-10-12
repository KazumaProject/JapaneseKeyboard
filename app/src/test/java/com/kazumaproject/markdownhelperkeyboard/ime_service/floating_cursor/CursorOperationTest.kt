package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor

import org.junit.Assert.*
import org.junit.Test

class CursorOperationTest {
    @Test fun allDirectionsRespectPreeditAndSegmentConversion() {
        for (direction in CursorDirection.entries) {
            assertEquals(CursorOperation.EDITOR, resolveCursorOperation(direction, false, false, false))
            assertEquals(if (direction.horizontal) CursorOperation.PREEDIT else CursorOperation.NONE,
                resolveCursorOperation(direction, true, false, false))
            assertEquals(CursorOperation.CANDIDATE, resolveCursorOperation(direction, true, true, false))
            assertEquals(if (direction.horizontal) CursorOperation.SEGMENT else CursorOperation.CANDIDATE,
                resolveCursorOperation(direction, true, true, true))
        }
    }

    @Test fun trackpadPreservesRemainderChangesDirectionAndResetsBetweenTouches() {
        val directions = mutableListOf<CursorDirection>()
        val gesture = CursorTrackpadGesture(20f, directions::add)
        gesture.begin(100f, 100f)
        gesture.move(119f, 100f)
        assertTrue(directions.isEmpty())
        gesture.move(165f, 103f)
        assertEquals(List(3) { CursorDirection.RIGHT }, directions)
        gesture.move(135f, 103f)
        assertEquals(CursorDirection.LEFT, directions.last())
        gesture.move(135f, 164f)
        assertEquals(List(3) { CursorDirection.DOWN }, directions.takeLast(3))
        directions.clear()
        gesture.begin(500f, 500f)
        gesture.move(500f, 480f)
        assertEquals(listOf(CursorDirection.UP), directions)
    }
}
