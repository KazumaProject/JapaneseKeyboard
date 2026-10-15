package com.kazumaproject.markdownhelperkeyboard.ime_service.bunsetsu_diff

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiQueryResult
import org.junit.Assert.*
import org.junit.Test

class BunsetsuDiffSnapshotTest {
    private val readings = listOf("きのうは", "こうえんで", "しゃしんを", "とりました")
    private val reading = readings.joinToString("")
    private val splits = readings.runningFold(0) { sum, s -> sum + s.length }.drop(1).dropLast(1)
    private val outputs = listOf(
        listOf("昨日は", "公園で", "写真を", "取りました"),
        listOf("昨日は", "公園で", "写真を", "撮りました"),
        listOf("昨日は", "講演で", "写真を", "撮りました"),
    )
    private fun result(variants: List<List<String>> = outputs): KanaKanjiQueryResult {
        val candidates = variants.map { Candidate(string = it.joinToString(""), type = 1, length = reading.length.toUByte(), score = 1000) }
        val paths = candidates.zip(variants).associate { (candidate, pieces) ->
            var offset = 0
            candidate.string to pieces.mapIndexed { i, output ->
                CandidateConversionSegment(offset, offset + readings[i].length, output).also { offset += readings[i].length }
            }
        }
        return KanaKanjiQueryResult(candidates, BunsetsuCandidateResult(candidates, listOf(splits)), paths)
    }

    @Test fun convertedSentenceShowsOnlyDifferingSegmentsAndNeverSlicesByKanjiLength() {
        val snapshot = buildBunsetsuDiffSnapshot(reading, outputs[0].joinToString(""), result())!!
        assertEquals(listOf(1, 3), snapshot.differingIndices)
        assertEquals(listOf("取りました", "撮りました"), snapshot.segments[3].candidates.map { it.string })
        assertEquals(listOf("公園で", "講演で"), snapshot.segments[1].candidates.map { it.string })
        assertEquals("昨日は公園で写真を取りました", snapshot.text)
        assertTrue(snapshot.segments.all { it.candidatesLoaded })
    }

    @Test fun candidateAnchorsUseOutputOffsetsAndKeepTheirOriginalMappingAfterSelection() {
        val snapshot = buildBunsetsuDiffSnapshot(reading, reading, result())!!
        val candidate = outputs[0].joinToString("")
        assertEquals(listOf(0..2, 3..5, 6..8, 9..13), snapshot.candidateRanges[candidate])
        assertEquals(listOf(0..3, 4..8, 9..13, 14..18), snapshot.candidateRanges[reading])
        val selected = snapshot.segments[3].copy(displayText = "撮りました", selectedIndex = 2)
        val changed = snapshot.copy(segments = snapshot.segments.toMutableList().also { it[3] = selected })
        assertEquals(snapshot.candidateRanges, changed.candidateRanges)
        assertEquals("きのうはこうえんでしゃしんを撮りました", changed.text)
    }

    @Test fun rawPreviewPreservesKanaAndCyclingOneSegmentPreservesEveryOtherDisplay() {
        val snapshot = buildBunsetsuDiffSnapshot(reading, reading, result())!!
        assertEquals(reading, snapshot.text)
        assertEquals(listOf(1, 3), snapshot.differingIndices)
        val segment = snapshot.segments[3]
        val selected = segment.copy(selectedIndex = 1, displayText = segment.candidates[1].string)
        val changed = snapshot.copy(segments = snapshot.segments.toMutableList().also { it[3] = selected })
        assertEquals("きのうはこうえんでしゃしんを取りました", changed.text)
        assertEquals(snapshot.segments.take(3), changed.segments.take(3))
        assertEquals(segment.candidates, changed.segments[3].candidates)
    }

    @Test fun oneConvertedChoiceDoesNotMakeRawKanaLookAmbiguous() {
        val snapshot = buildBunsetsuDiffSnapshot(reading, reading, result(listOf(outputs[0])))!!
        assertTrue(snapshot.differingIndices.isEmpty())
        assertEquals(reading, snapshot.text)
    }

    @Test fun unknownDisplayAndMissingOrInvalidPathsAreRejected() {
        assertNull(buildBunsetsuDiffSnapshot(reading, "推測できない文章", result()))
        assertNull(buildBunsetsuDiffSnapshot(reading, reading, result().copy(candidateSegmentsByString = emptyMap())))
        val broken = result().copy(candidateSegmentsByString = result().candidateSegmentsByString.mapValues { (_, nodes) -> nodes.drop(1) })
        assertNull(buildBunsetsuDiffSnapshot(reading, reading, broken))
        assertNull(buildBunsetsuDiffSnapshot("", "", result()))
    }

    @Test fun boundariesInsideLatticeNodesAreNotInvented() {
        val original = result()
        val brokenPaths = original.candidateSegmentsByString.mapValues { (_, nodes) ->
            listOf(CandidateConversionSegment(0, nodes[1].inputEnd, nodes[0].output + nodes[1].output)) + nodes.drop(2)
        }
        val incompatible = original.copy(candidateSegmentsByString = brokenPaths)
        assertNull(buildBunsetsuDiffSnapshot(reading, outputs[0].joinToString(""), incompatible))
        val raw = buildBunsetsuDiffSnapshot(reading, reading, incompatible)!!
        assertFalse(0 in raw.differingIndices)
        assertFalse(1 in raw.differingIndices)
        assertTrue(3 in raw.differingIndices)
    }

    @Test fun duplicatesAndForbiddenSegmentAlternativesDoNotCreateBubbles() {
        val duplicate = result(listOf(outputs[0], outputs[0]))
        assertTrue(buildBunsetsuDiffSnapshot(reading, reading, duplicate)!!.differingIndices.isEmpty())
        val filtered = buildBunsetsuDiffSnapshot(reading, reading, result()) { _, text -> text != "講演で" }!!
        assertEquals(listOf(3), filtered.differingIndices)
    }
}
