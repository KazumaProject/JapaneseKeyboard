package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import org.junit.Assert.*
import org.junit.Test

class SegmentCandidateModelTest {
    private fun segment(reading: String, text: String) = BunsetsuSegmentState(
        reading, text, hasConvertedDisplay = true, candidatesLoaded = true,
    )

    @Test fun boundaryInsideKanjiRequeriesBothFragmentsAndPreservesOtherChoices() {
        val first = segment("あしたは", "明日は")
        val last = segment("いきます", "行きます")
        val result = replaceSegmentRange("あしたはとうきょうにいきます", listOf(first, segment("とうきょうに", "東京に"), last), 4, 7)
        assertSame(first, result[0])
        assertEquals(listOf("あしたは", "とうき", "ょうに", "いきます"), result.map { it.reading })
        assertEquals("とうき", result[1].displayText)
        assertFalse(result[1].hasConvertedDisplay)
        assertFalse(result[2].hasConvertedDisplay)
        assertSame(last, result[3])
    }

    @Test fun treeChoiceWithDifferentBoundariesOnlyReplacesOverlappingRange() {
        val last = segment("いきます", "行きます")
        val result = replaceSegmentRange("あしたはとうきょうにいきます",
            listOf(segment("あしたは", "明日は"), segment("とうきょうに", "東京に"), last), 0, 10, "明日はとうきょうに")
        assertEquals(listOf("明日はとうきょうに", "行きます"), result.map { it.displayText })
        assertSame(last, result[1])
        assertEquals("あしたはとうきょうにいきます", result.joinToString("") { it.reading })
    }

    @Test fun oneSegmentCanBeSplitAndAdjacentSegmentsCanBeMerged() {
        val split = replaceSegmentRange("きょうは", listOf(segment("きょうは", "今日は")), 0, 3)
        assertEquals(listOf("きょう", "は"), split.map { it.reading })
        val merged = replaceSegmentRange("きょうは", split, 0, 4)
        assertEquals(listOf("きょうは"), merged.map { it.reading })
    }

    @Test fun boundariesDoNotSplitSurrogatesOrCombiningMarks() {
        assertEquals(listOf(0, 1, 3, 5), readingBoundaries("あ😀か\u3099"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotCreateEmptySegment() {
        replaceSegmentRange("よみ", listOf(segment("よみ", "読み")), 1, 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotCutInsideSurrogatePair() {
        replaceSegmentRange("あ😀", listOf(segment("あ😀", "あ😀")), 0, 2)
    }

    @Test fun treeSharesPrefixesWithoutInventingCrossBranchPaths() {
        val prefix = SegmentChoice(0, 4, "明日は")
        val a = SegmentChoice(4, 10, "東京に")
        val b = a.copy(text = "とうきょうに")
        val suffix = SegmentChoice(10, 14, "行きます")
        val root = buildCandidateTree(listOf(listOf(prefix, a, suffix), listOf(prefix, b, suffix)))
        assertEquals(1, root.children.size)
        assertEquals(listOf(a, b), root.children.single().children.map { it.choice })
        assertTrue(root.children.single().children.all { it.children.single().choice == suffix })
    }

    @Test fun actualReadingOffsetsGroupWordsIntoBunsetsuAndMissingPathsStayWhole() {
        val candidates = listOf(
            Candidate("今日は晴れ", 1, 7u, 100),
            Candidate("きょうは晴れ", 1, 7u, 200),
        )
        val snapshot = BunsetsuConversionSnapshot("きょうははれ", candidates,
            mapOf("今日は晴れ" to listOf(CandidateConversionSegment(0, 3, "今日"),
                CandidateConversionSegment(3, 4, "は"), CandidateConversionSegment(4, 7, "晴れ"))),
            candidateSplitPositions = mapOf("今日は晴れ" to listOf(4)))
        // Fixture intentionally has an invalid last offset: invalid paths must remain atomic.
        assertEquals(listOf(SegmentChoice(0, 6, "今日は晴れ")), candidateTreePaths(snapshot.copy(
            candidates = candidates.map { it.copy(length = 6u) }), 4)[0])
        val valid = snapshot.copy(input = "きょうははれる", paths = mapOf("今日は晴れ" to listOf(
            CandidateConversionSegment(0, 3, "今日"), CandidateConversionSegment(3, 4, "は"), CandidateConversionSegment(4, 7, "晴れ"))))
        assertEquals(listOf(SegmentChoice(0, 4, "今日は"), SegmentChoice(4, 7, "晴れ")), candidateTreePaths(valid, 4)[0])
        assertEquals(listOf(SegmentChoice(0, 7, "きょうは晴れ")), candidateTreePaths(valid, 4)[1])
    }
}
