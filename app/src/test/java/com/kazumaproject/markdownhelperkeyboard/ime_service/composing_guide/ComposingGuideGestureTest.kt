package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import org.junit.Assert.*
import org.junit.Test

class ComposingGuideGestureTest {
    private val initial = GuideBounds(100, 100, 300, 250)
    private fun reducer() = ComposingGuideGesture(initial, GuideBounds(0, 0, 800, 700), 200, 192)

    @Test fun eachEdgeMovesIndependentlyAndLeavesOppositeEdgeFixed() {
        val expected = mapOf(GuideHandle.LEFT to GuideBounds(80, 100, 320, 250),
            GuideHandle.RIGHT to GuideBounds(100, 100, 320, 250),
            GuideHandle.TOP to GuideBounds(100, 80, 300, 270),
            GuideHandle.BOTTOM to GuideBounds(100, 100, 300, 270))
        expected.forEach { (edge, target) ->
            val gesture = reducer()
            assertTrue(gesture.add(4, edge, GuidePoint(0f, 0f)))
            val amount = if (edge == GuideHandle.LEFT || edge == GuideHandle.TOP) -20f else 20f
            assertEquals(target, gesture.move(mapOf(4 to GuidePoint(amount, amount))))
        }
    }

    @Test fun oppositeEdgesCanBeResizedWithTwoIndependentPointerIds() {
        val gesture = reducer()
        gesture.add(8, GuideHandle.LEFT, GuidePoint(100f, 200f))
        gesture.add(2, GuideHandle.RIGHT, GuidePoint(400f, 200f))
        assertEquals(GuideBounds(50, 100, 410, 250), gesture.move(mapOf(
            2 to GuidePoint(460f, 200f), 8 to GuidePoint(50f, 200f))))
    }

    @Test fun adjacentEdgesResizeBothDimensions() {
        val gesture = reducer()
        gesture.add(1, GuideHandle.TOP, GuidePoint(250f, 100f))
        gesture.add(9, GuideHandle.RIGHT, GuidePoint(400f, 200f))
        assertEquals(GuideBounds(100, 60, 350, 290), gesture.move(mapOf(
            1 to GuidePoint(250f, 60f), 9 to GuidePoint(450f, 200f))))
    }

    @Test fun addingAndRemovingPointersDoesNotJump() {
        val gesture = reducer()
        gesture.add(1, GuideHandle.LEFT, GuidePoint(100f, 200f))
        val first = gesture.move(mapOf(1 to GuidePoint(80f, 200f)))
        gesture.add(9, GuideHandle.BOTTOM, GuidePoint(250f, 350f))
        assertEquals(first, gesture.move(mapOf(1 to GuidePoint(80f, 200f), 9 to GuidePoint(250f, 350f))))
        val resized = gesture.move(mapOf(1 to GuidePoint(70f, 200f), 9 to GuidePoint(250f, 390f)))
        gesture.remove(1)
        assertEquals(resized, gesture.move(mapOf(9 to GuidePoint(250f, 390f))))
        assertEquals(resized.copy(height = resized.height + 10), gesture.move(mapOf(9 to GuidePoint(250f, 400f))))
    }

    @Test fun duplicateEdgeThirdFingerAndMoveResizeMixturesAreIgnored() {
        val gesture = reducer()
        assertTrue(gesture.add(0, GuideHandle.LEFT, GuidePoint(0f, 0f)))
        assertFalse(gesture.add(1, GuideHandle.LEFT, GuidePoint(0f, 0f)))
        assertFalse(gesture.add(1, GuideHandle.MOVE, GuidePoint(0f, 0f)))
        assertTrue(gesture.add(2, GuideHandle.RIGHT, GuidePoint(0f, 0f)))
        assertFalse(gesture.add(3, GuideHandle.TOP, GuidePoint(0f, 0f)))
    }

    @Test fun crossingEdgesKeepsMinimumSizeAndStaysWithinArea() {
        val gesture = reducer()
        gesture.add(1, GuideHandle.LEFT, GuidePoint(100f, 200f))
        gesture.add(2, GuideHandle.RIGHT, GuidePoint(400f, 200f))
        val result = gesture.move(mapOf(1 to GuidePoint(1000f, 200f), 2 to GuidePoint(-1000f, 200f)))
        assertEquals(200, result.width)
        assertTrue(result.x >= 0 && result.right <= 800)
    }

    @Test fun moveClampsAndCancellationReturnsOriginalEvenAfterRebasing() {
        val gesture = reducer()
        gesture.add(3, GuideHandle.MOVE, GuidePoint(200f, 100f))
        assertEquals(GuideBounds(0, 450, 300, 250), gesture.move(mapOf(3 to GuidePoint(-200f, 1100f))))
        gesture.remove(3)
        assertEquals(initial, gesture.cancel())
    }
}
