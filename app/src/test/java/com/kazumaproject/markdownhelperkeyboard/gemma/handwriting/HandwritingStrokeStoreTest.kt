package com.kazumaproject.markdownhelperkeyboard.gemma.handwriting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandwritingStrokeStoreTest {
    private val first = listOf(
        HandwritingPoint(0.1f, 0.1f),
        HandwritingPoint(0.2f, 0.2f),
    )
    private val second = listOf(
        HandwritingPoint(0.4f, 0.1f),
        HandwritingPoint(0.4f, 0.8f),
    )

    @Test
    fun undoAndRedoOperateOneStrokeAtATime() {
        val store = HandwritingStrokeStore()
        store.addStroke(first)
        store.addStroke(second)

        assertEquals(2, store.strokes.size)
        assertTrue(store.undo())
        assertEquals(1, store.strokes.size)
        assertTrue(store.redo())
        assertEquals(2, store.strokes.size)
    }

    @Test
    fun addingStrokeAfterUndoDropsRedoHistory() {
        val store = HandwritingStrokeStore()
        store.addStroke(first)
        store.addStroke(second)
        store.undo()

        store.addStroke(listOf(HandwritingPoint(0.8f, 0.8f)))

        assertFalse(store.canRedo)
        assertFalse(store.redo())
    }

    @Test
    fun clearCanBeUndone() {
        val store = HandwritingStrokeStore()
        store.addStroke(first)

        assertTrue(store.clear())
        assertTrue(store.isEmpty)
        assertTrue(store.undo())
        assertEquals(1, store.strokes.size)
    }
}
