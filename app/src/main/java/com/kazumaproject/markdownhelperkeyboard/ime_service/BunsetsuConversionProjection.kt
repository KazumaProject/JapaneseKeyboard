package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment

/** Candidates and their paths belong to the same whole-input query. */
internal data class BunsetsuConversionSnapshot(
    val input: String,
    val candidates: List<Candidate>,
    val paths: Map<String, List<CandidateConversionSegment>>,
    val splitPatterns: List<List<Int>> = emptyList(),
    val initialSplitPositions: List<Int> = emptyList(),
)

internal data class BunsetsuSegmentState(
    val reading: String,
    val displayText: String,
    val candidates: List<Candidate> = emptyList(),
    val selectedIndex: Int = 0,
    val overrideDisplayCandidate: Candidate? = null,
    val hasConvertedDisplay: Boolean = false,
    val candidatesLoaded: Boolean = false,
)

/** Project entire lattice nodes onto reading ranges; never slice the converted output by length. */
internal fun buildConvertedBunsetsuSegments(
    input: String,
    splitPositions: List<Int>,
    snapshot: BunsetsuConversionSnapshot?,
    displayText: (Candidate) -> String = { it.string },
): List<BunsetsuSegmentState> {
    if (input.isEmpty()) return emptyList()
    val boundaries = listOf(0) + splitPositions.filter { it in 1 until input.length }
        .distinct().sorted() + input.length
    val current = snapshot?.takeIf { it.input == input }
    // Choose one coherent full path, rather than mixing words from different N-best paths.
    val paths = current?.candidates?.mapNotNull { candidate ->
        current.paths[candidate.string]?.takeIf { nodes ->
            nodes.isNotEmpty() && nodes.first().inputStart == 0 &&
                nodes.last().inputEnd == input.length &&
                nodes.all { it.inputStart < it.inputEnd } &&
                nodes.zipWithNext().all { (left, right) -> left.inputEnd == right.inputStart } &&
                nodes.joinToString("") { it.output } == candidate.string
        }
    }.orEmpty()
    val path = paths.firstOrNull { nodes ->
        boundaries.all { boundary -> boundary == 0 || nodes.any { it.inputEnd == boundary } }
    } ?: paths.firstOrNull()
    return boundaries.zipWithNext().map { (start, end) ->
        val reading = input.substring(start, end)
        val wholeCandidate = current?.candidates?.firstOrNull()?.takeIf {
            start == 0 && end == input.length && it.length.toInt() == input.length &&
                it.sourceId == null && it.presentation == null && it.commitText == it.string
        }?.let(displayText)
        val output = wholeCandidate ?: path?.takeIf { nodes ->
            nodes.any { it.inputStart == start } && nodes.any { it.inputEnd == end }
        }?.filter { it.inputStart >= start && it.inputEnd <= end }?.joinToString("") { it.output }
        BunsetsuSegmentState(
            reading = reading,
            displayText = output ?: reading,
            hasConvertedDisplay = output != null,
        )
    }
}

/** Loading alternatives must not replace the whole-sentence choice with an isolated-word choice. */
internal fun mergeBunsetsuCandidates(
    segment: BunsetsuSegmentState,
    candidates: List<Candidate>,
    displayText: (Candidate) -> String = { it.string },
): BunsetsuSegmentState {
    val initialText = if (segment.hasConvertedDisplay) segment.displayText else {
        candidates.firstOrNull()?.let(displayText) ?: segment.reading
    }
    val initialCandidate = candidates.firstOrNull { displayText(it) == initialText }
        ?: Candidate(
            string = initialText,
            type = if (initialText == segment.reading) 3 else 1,
            length = segment.reading.length.toUByte(),
            score = 3000,
            yomi = segment.reading,
        )
    val merged = (listOf(initialCandidate) + candidates).distinctBy(displayText)
    return segment.copy(
        displayText = initialText,
        candidates = merged,
        selectedIndex = 0,
        hasConvertedDisplay = true,
        candidatesLoaded = true,
    )
}
