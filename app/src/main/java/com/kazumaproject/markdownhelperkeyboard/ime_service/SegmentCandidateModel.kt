package com.kazumaproject.markdownhelperkeyboard.ime_service

import java.text.BreakIterator
import java.util.Locale

internal const val SEGMENT_TREE_ENABLED_KEY = "segment_tree_enabled"
internal const val SEGMENT_CANDIDATE_STYLE_KEY = "candidate_display_method"
internal const val SEGMENT_CANDIDATE_STYLE = "segments"

/** Offsets always refer to the reading, never to the length of a converted label. */
internal data class SegmentChoice(val start: Int, val end: Int, val text: String)

internal class CandidateGraphNode(val choice: SegmentChoice?) {
    val children = mutableListOf<CandidateGraphNode>()
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

/** Minimize a prefix trie by sharing equivalent continuations; keep the exact input path set. */
internal fun buildCandidateGraph(paths: List<List<SegmentChoice>>): CandidateGraphNode {
    val root = CandidateGraphNode(null)
    paths.forEach { path ->
        var parent = root
        path.forEach { choice ->
            val node = parent.children.firstOrNull { it.choice == choice }
                ?: CandidateGraphNode(choice).also { parent.children.add(it) }
            parent = node
        }
    }
    data class Key(val choice: SegmentChoice?, val children: List<Int>)
    data class Canonical(val node: CandidateGraphNode, val id: Int)
    val continuations = linkedMapOf<Key, Canonical>()
    fun share(node: CandidateGraphNode): Canonical {
        val children = node.children.map(::share)
        val key = Key(node.choice, children.map { it.id }.sorted())
        return continuations.getOrPut(key) {
            Canonical(CandidateGraphNode(node.choice).apply { this.children.addAll(children.map { it.node }) }, continuations.size)
        }
    }
    return share(root).node
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

/** Move complete segment states together with their readings and selected candidates. */
internal fun moveSegment(
    segments: List<BunsetsuSegmentState>, from: Int, to: Int,
): List<BunsetsuSegmentState> {
    require(from in segments.indices && to in segments.indices)
    return segments.toMutableList().apply { add(to, removeAt(from)) }
}
