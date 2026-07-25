package com.kazumaproject.markdownhelperkeyboard.gemma.handwriting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandwritingStrokeSegmenterTest {
    @Test
    fun segment_splitsClearlySeparatedCharacterGroups() {
        val leftVertical = stroke(0.10f, 0.20f, 0.10f, 0.80f)
        val leftCrossbar = stroke(0.10f, 0.50f, 0.25f, 0.50f)
        val rightVertical = stroke(0.55f, 0.20f, 0.55f, 0.80f)

        val result = HandwritingStrokeSegmenter.segment(
            listOf(leftVertical, rightVertical, leftCrossbar),
        )

        assertEquals(2, result.size)
        assertEquals(listOf(leftVertical, leftCrossbar), result[0])
        assertEquals(listOf(rightVertical), result[1])
    }

    @Test
    fun segment_keepsNearbyDetachedMarksWithCharacter() {
        val main = stroke(0.30f, 0.20f, 0.45f, 0.80f)
        val nearbyMark = stroke(0.47f, 0.15f, 0.48f, 0.17f)

        assertEquals(
            listOf(listOf(main, nearbyMark)),
            HandwritingStrokeSegmenter.segment(listOf(main, nearbyMark)),
        )
    }

    @Test
    fun segment_returnsEmptyForNoStrokes() {
        assertTrue(HandwritingStrokeSegmenter.segment(emptyList()).isEmpty())
    }

    private fun stroke(vararg coordinates: Float): HandwritingStroke {
        return HandwritingStroke(
            coordinates.toList().chunked(2).map { (x, y) -> HandwritingPoint(x, y) },
        )
    }
}
