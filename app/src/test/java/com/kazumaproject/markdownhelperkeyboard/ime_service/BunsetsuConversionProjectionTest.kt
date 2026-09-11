package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import org.junit.Assert.*
import org.junit.Test

class BunsetsuConversionProjectionTest {
    private val reading = "あしたはとうきょうにいきます"
    private val path = listOf(
        CandidateConversionSegment(0, 3, "明日"),
        CandidateConversionSegment(3, 4, "は"),
        CandidateConversionSegment(4, 9, "東京"),
        CandidateConversionSegment(9, 10, "に"),
        CandidateConversionSegment(10, 12, "行き"),
        CandidateConversionSegment(12, 14, "ます"),
    )
    private fun candidate(text: String, length: Int = reading.length) = Candidate(
        string = text, type = 1, length = length.toUByte(), score = 1000,
    )
    private fun snapshot(nodes: List<CandidateConversionSegment> = path) = BunsetsuConversionSnapshot(
        reading, listOf(candidate("明日は東京に行きます")), mapOf("明日は東京に行きます" to nodes),
    )

    @Test
    fun unsplitInputKeepsPromotedCandidateEvenWhenOnlyLowerCandidateHasPath() {
        val promoted = candidate("あすは東京に行きます")
        val source = snapshot().copy(candidates = listOf(promoted) + snapshot().candidates)
        val segment = buildConvertedBunsetsuSegments(reading, emptyList(), source).single()
        assertEquals(promoted.string, segment.displayText)
    }

    @Test
    fun readingCorrectionUsesDisplayTextAndDoesNotCreateDuplicateAlternatives() {
        val corrected = candidate("correction-markup", 2).copy(type = 15)
        val segment = BunsetsuSegmentState("よみ", "表記", hasConvertedDisplay = true)
        val loaded = mergeBunsetsuCandidates(segment, listOf(corrected)) { "表記" }
        assertEquals("表記", loaded.displayText)
        assertEquals(listOf(corrected), loaded.candidates)
    }

    @Test
    fun firstConversionDisplaysEverySegmentUsingReadingOffsets() {
        val segments = buildConvertedBunsetsuSegments(reading, listOf(4, 10), snapshot())
        assertEquals(listOf("明日は", "東京に", "行きます"), segments.map { it.displayText })
        assertEquals(reading, segments.joinToString("") { it.reading })
        assertTrue(segments.all { it.hasConvertedDisplay })
        assertFalse(segments.any { it.candidatesLoaded })
    }

    @Test
    fun loadingSecondSegmentPreservesSentenceChoiceDespiteDifferentLocalRanking() {
        val segments = buildConvertedBunsetsuSegments(reading, listOf(4, 10), snapshot())
        val tokyo = candidate("東京に", 6)
        val alternative = candidate("とうきょうに", 6)
        val loaded = mergeBunsetsuCandidates(segments[1], listOf(alternative, tokyo))
        assertEquals("東京に", loaded.displayText)
        assertEquals(listOf(tokyo, alternative), loaded.candidates)
        assertEquals(0, loaded.selectedIndex)
        assertTrue(loaded.candidatesLoaded)
        val updated = segments.toMutableList().apply { this[1] = loaded }
        assertEquals("明日は東京に行きます", updated.joinToString("") { it.displayText })
    }

    @Test
    fun wholeSentenceChoiceSurvivesMissingLocalCandidate() {
        val segment = buildConvertedBunsetsuSegments(reading, listOf(4, 10), snapshot())[2]
        val loaded = mergeBunsetsuCandidates(segment, listOf(candidate("いきます", 4)))
        assertEquals("行きます", loaded.displayText)
        assertEquals(listOf("行きます", "いきます"), loaded.candidates.map { it.string })
    }

    @Test
    fun changingSplitPatternCombinesCompleteNodes() {
        val segments = buildConvertedBunsetsuSegments(reading, listOf(10), snapshot())
        assertEquals(listOf("明日は東京に", "行きます"), segments.map { it.displayText })
    }

    @Test
    fun boundaryInsideNodeRequiresConversionInsteadOfSlicingKanji() {
        val segments = buildConvertedBunsetsuSegments(reading, listOf(2, 10), snapshot())
        assertFalse(segments[0].hasConvertedDisplay)
        assertFalse(segments[1].hasConvertedDisplay)
        assertEquals("行きます", segments[2].displayText)
        assertEquals(reading, segments.joinToString("") { it.reading })
        val loaded = mergeBunsetsuCandidates(segments[0], listOf(candidate("足", 2)))
        assertEquals("足", loaded.displayText)
    }

    @Test
    fun staleOrIncompletePathsAreNotApplied() {
        val stale = snapshot().copy(input = "べつのよみ")
        val gap = snapshot(path.filterNot { it.inputStart == 3 })
        val incorrectOutput = snapshot(path.map { if (it.inputStart == 0) it.copy(output = "昨日") else it })
        for (invalid in listOf(stale, gap, incorrectOutput, null)) {
            val segments = buildConvertedBunsetsuSegments(reading, listOf(4, 10), invalid)
            assertFalse(segments.any { it.hasConvertedDisplay })
            assertEquals(reading, segments.joinToString("") { it.displayText })
        }
    }

    @Test
    fun unknownReadingHasSelectableFallbackAndEmptyInputHasNoSegments() {
        val segment = buildConvertedBunsetsuSegments("ほげ", emptyList(), null).single()
        val loaded = mergeBunsetsuCandidates(segment, emptyList())
        assertEquals("ほげ", loaded.displayText)
        assertEquals("ほげ", loaded.candidates.single().string)
        assertTrue(loaded.candidatesLoaded)
        assertTrue(buildConvertedBunsetsuSegments("", listOf(1), null).isEmpty())
    }

    @Test
    fun splitNormalizationDoesNotLoseOrDuplicateText() {
        val segments = buildConvertedBunsetsuSegments(reading, listOf(10, -1, 4, 4, 99), snapshot())
        assertEquals(listOf("明日は", "東京に", "行きます"), segments.map { it.displayText })
    }
}
