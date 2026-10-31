package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VerticalSegmentModelTest {
    private fun segment(reading: String, text: String) =
        BunsetsuSegmentState(reading, text, hasConvertedDisplay = true)

    @Test fun shrinkingAndExtendingCreatesAndDeletesBoundaries() {
        val original = listOf(segment("あした", "明日"), segment("はれ", "晴れ"))
        val split = replaceSegmentRange("あしたはれ", original, 0, 2)
        assertEquals(listOf("あし", "た", "はれ"), split.map { it.reading })
        assertEquals("晴れ", split.last().displayText)
        val merged = replaceSegmentRange("あしたはれ", split, 0, 5)
        assertEquals(listOf("あしたはれ"), merged.map { it.reading })
        assertFalse(merged.single().hasConvertedDisplay)
    }

    @Test fun movingSegmentsKeepsTheirReadingsAndSelectedTextTogether() {
        val original = listOf(segment("あした", "明日"), segment("はれ", "晴れ"))
        assertEquals(listOf("晴れ", "明日"), moveSegment(original, 1, 0).map { it.displayText })
        assertEquals(listOf("はれ", "あした"), moveSegment(original, 1, 0).map { it.reading })
        assertEquals("明日", original.first().displayText)
    }

    @Test fun inputEditsRetainUnaffectedBoundaries() {
        assertEquals(listOf(3, 5), remapSegmentBoundaries("あいうえお", "あいかうえお", listOf(2, 4)))
        assertEquals(listOf(2), remapSegmentBoundaries("あいうえお", "あいお", listOf(2, 4)))
        assertEquals(listOf(2, 4), remapSegmentBoundaries("あいうえお", "あいうえおか", listOf(2, 4)))
    }

    @Test fun boundariesNeverSplitCombiningMarksOrEmoji() {
        val input = "か\u3099👨‍👩‍👧‍👦あ"
        val boundaries = readingBoundaries(input)
        assertEquals(listOf(0, 2, input.length - 1, input.length), boundaries)
        assertTrue(remapSegmentBoundaries(input, input + "い", listOf(2, input.length - 1)).all { it in boundaries })
    }
}
