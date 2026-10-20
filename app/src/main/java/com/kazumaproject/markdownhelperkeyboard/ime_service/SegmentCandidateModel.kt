package com.kazumaproject.markdownhelperkeyboard.ime_service

import java.text.BreakIterator
import java.util.Locale

internal const val SEGMENT_CANDIDATE_STYLE_KEY = "candidate_display_method"
internal const val SEGMENT_CANDIDATE_STYLE = "segments"

/** Offsets always refer to the reading, never to the length of a converted label. */
internal data class SegmentChoice(val start: Int, val end: Int, val text: String)

internal class CandidateTreeNode(val choice: SegmentChoice?) {
    val children = mutableListOf<CandidateTreeNode>()
}

internal fun readingBoundaries(input: String): List<Int> {
    val iterator = BreakIterator.getCharacterInstance(Locale.JAPANESE)
    iterator.setText(input)
    return buildList {
        var offset = iterator.first()
        while (offset != BreakIterator.DONE) {
            add(offset)
            offset = iterator.next()
        }
    }
}

internal fun segmentChoices(segments: List<BunsetsuSegmentState>): List<SegmentChoice> {
    var offset = 0
    return segments.map { segment ->
        SegmentChoice(offset, offset + segment.reading.length, segment.displayText).also {
            offset = it.end
        }
    }
}

/** A prefix trie retains actual candidate paths; it does not invent cross-branch sentences. */
internal fun buildCandidateTree(paths: List<List<SegmentChoice>>): CandidateTreeNode {
    val root = CandidateTreeNode(null)
    paths.forEach { path ->
        var parent = root
        path.forEach { choice ->
            val node = parent.children.firstOrNull { it.choice == choice }
                ?: CandidateTreeNode(choice).also { parent.children.add(it) }
            parent = node
        }
    }
    return root
}

internal fun candidateTreePaths(snapshot: BunsetsuConversionSnapshot, limit: Int): List<List<SegmentChoice>> =
    snapshot.candidates.asSequence().filter {
        it.length.toInt() == snapshot.input.length && it.sourceId == null && it.presentation == null &&
            it.commitText == it.string
    }.take(limit.coerceIn(2, 16)).map { candidate ->
        val nodes = snapshot.paths[candidate.string].orEmpty()
        val valid = nodes.isNotEmpty() && nodes.first().inputStart == 0 &&
            nodes.last().inputEnd == snapshot.input.length &&
            nodes.all { it.inputStart >= 0 && it.inputStart < it.inputEnd && it.inputEnd <= snapshot.input.length } &&
            nodes.zipWithNext().all { (a, b) -> a.inputEnd == b.inputStart } &&
            nodes.joinToString("") { it.output } == candidate.string
        if (!valid) listOf(SegmentChoice(0, snapshot.input.length, candidate.string))
        else {
            val splits = snapshot.candidateSplitPositions[candidate.string]
            val boundaries = listOf(0) + (splits ?: nodes.dropLast(1).map { it.inputEnd }) + snapshot.input.length
            if (boundaries != boundaries.distinct().sorted() ||
                boundaries.any { it != 0 && nodes.none { n -> n.inputEnd == it } }) {
                nodes.map { SegmentChoice(it.inputStart, it.inputEnd, it.output) }
            } else boundaries.zipWithNext().map { (start, end) ->
                SegmentChoice(start, end, nodes.filter { it.inputStart >= start && it.inputEnd <= end }.joinToString("") { it.output })
            }
        }
    }.toList()

/** Reconvert only overlapping ranges. A cut through a kanji node never slices its output. */
internal fun replaceSegmentRange(
    input: String,
    segments: List<BunsetsuSegmentState>,
    start: Int,
    end: Int,
    text: String? = null,
): List<BunsetsuSegmentState> {
    val legal = readingBoundaries(input)
    require(start in legal && end in legal && start < end)
    require(segments.joinToString("") { it.reading } == input)
    val old = segmentChoices(segments)
    val boundaries = (listOf(0, input.length, start, end) + old.map { it.end })
        .filter { it <= start || it >= end }.distinct().sorted()
    return boundaries.zipWithNext().map { (left, right) ->
        if (left == start && right == end) {
            BunsetsuSegmentState(input.substring(left, right), text ?: input.substring(left, right),
                hasConvertedDisplay = text != null)
        } else {
            val existing = old.indexOfFirst { it.start == left && it.end == right }
            if (existing >= 0) segments[existing]
            else BunsetsuSegmentState(input.substring(left, right), input.substring(left, right))
        }
    }
}
