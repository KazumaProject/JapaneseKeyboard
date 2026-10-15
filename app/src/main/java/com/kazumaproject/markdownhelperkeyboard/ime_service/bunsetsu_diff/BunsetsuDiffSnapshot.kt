package com.kazumaproject.markdownhelperkeyboard.ime_service.bunsetsu_diff

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiQueryResult
import com.kazumaproject.markdownhelperkeyboard.ime_service.BunsetsuSegmentState

internal data class BunsetsuDiffSnapshot(
    val reading: String,
    val segments: List<BunsetsuSegmentState>,
    val splitPositions: List<Int>,
    val candidateRanges: Map<String, List<IntRange>> = emptyMap(),
) {
    val text: String get() = segments.joinToString("") { it.displayText }
    val differingIndices: List<Int> get() = segments.indices.filter { segments[it].candidates.size > 1 }
}

/** Only complete lattice nodes may be projected onto a reading range. */
internal fun buildBunsetsuDiffSnapshot(
    reading: String,
    displayedText: String,
    result: KanaKanjiQueryResult,
    allowed: (String, String) -> Boolean = { _, _ -> true },
): BunsetsuDiffSnapshot? {
    if (reading.isEmpty() || reading.length > UByte.MAX_VALUE.toInt()) return null
    val paths = result.candidates.asSequence().filter {
        it.length.toInt() == reading.length && it.sourceId == null &&
            it.presentation == null && it.commitText == it.string
    }.mapNotNull { candidate ->
        result.candidateSegmentsByString[candidate.string]?.takeIf { nodes ->
            nodes.isNotEmpty() && nodes.first().inputStart == 0 &&
                nodes.last().inputEnd == reading.length &&
                nodes.all { it.inputStart >= 0 && it.inputEnd <= reading.length && it.inputStart < it.inputEnd } &&
                nodes.zipWithNext().all { (a, b) -> a.inputEnd == b.inputStart } &&
                nodes.joinToString("") { it.output } == candidate.string
        }?.let { candidate.string to it }
    }.distinctBy { it.first }.toList()
    val displayPath = paths.firstOrNull { it.first == displayedText }
    // A raw composition has a known identity mapping. Other displays need an exact path.
    if (displayedText != reading && displayPath == null) return null
    val base = displayPath ?: paths.firstOrNull() ?: return null
    val requestedSplits = result.bunsetsuResult?.let {
        it.splitPatternByCandidateString[base.first] ?: it.primarySplitPositions
    }.orEmpty()
    val splits = requestedSplits.filter { it in 1 until reading.length }.distinct().sorted()
    val boundaries = listOf(0) + splits + reading.length
    fun output(nodes: List<CandidateConversionSegment>, start: Int, end: Int): String? {
        if (nodes.none { it.inputStart == start } || nodes.none { it.inputEnd == end }) return null
        return nodes.filter { it.inputStart >= start && it.inputEnd <= end }.joinToString("") { it.output }
    }
    val segments = boundaries.zipWithNext().map { (start, end) ->
        val yomi = reading.substring(start, end)
        val current = if (displayedText == reading) yomi else output(base.second, start, end) ?: return null
        val alternatives = paths.mapNotNull { output(it.second, start, end) }.filter { allowed(yomi, it) }.distinct()
        // Raw kana alone must not turn an unambiguous conversion into a difference bubble.
        val choices = if (alternatives.size > 1) (listOf(current) + alternatives).distinct() else listOf(current)
        BunsetsuSegmentState(
            reading = yomi,
            displayText = current,
            candidates = choices.map { text ->
                Candidate(string = text, type = if (text == yomi) 3 else 1,
                    length = yomi.length.toUByte(), score = 3000, yomi = yomi)
            },
            selectedIndex = 0,
            hasConvertedDisplay = true,
            candidatesLoaded = true,
        )
    }
    val candidateRanges = paths.mapNotNull { (candidate, nodes) ->
        var offset = 0
        val ranges = boundaries.zipWithNext().map { (start, end) ->
            val piece = output(nodes, start, end) ?: return@mapNotNull null
            (offset until offset + piece.length).also { offset += piece.length }
        }
        candidate to ranges
    }.toMap().toMutableMap()
    // An ordinary literal candidate has an exact identity mapping as well.
    candidateRanges[reading] = boundaries.zipWithNext().map { (start, end) -> start until end }
    return BunsetsuDiffSnapshot(reading, segments, splits, candidateRanges).takeIf { it.text == displayedText }
}
