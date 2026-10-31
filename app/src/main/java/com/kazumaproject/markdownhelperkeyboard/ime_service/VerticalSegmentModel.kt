package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.icu.text.BreakIterator
import java.util.Locale

/** All offsets refer to UTF-16 indices in the reading, never to converted text. */
internal data class SegmentChoice(val start: Int, val end: Int, val text: String)

internal fun readingBoundaries(input: String): List<Int> {
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(input) }
    return buildList {
        var position = iterator.first()
        while (position != BreakIterator.DONE) {
            add(position)
            position = iterator.next()
        }
    }
}

internal fun segmentChoices(segments: List<BunsetsuSegmentState>): List<SegmentChoice> {
    var offset = 0
    return segments.map { segment ->
        SegmentChoice(offset, offset + segment.reading.length, segment.displayText).also { offset = it.end }
    }
}

/** Splitting never slices converted output; affected ranges are filled by a new whole-input path. */
internal fun replaceSegmentRange(
    input: String,
    segments: List<BunsetsuSegmentState>,
    start: Int,
    end: Int,
): List<BunsetsuSegmentState> {
    val legal = readingBoundaries(input)
    require(start in legal && end in legal && start < end)
    require(segments.joinToString("") { it.reading } == input)
    val previous = segmentChoices(segments)
    val boundaries = (listOf(0, input.length, start, end) + previous.map { it.end })
        .filter { it <= start || it >= end }.distinct().sorted()
    return boundaries.zipWithNext().map { (left, right) ->
        val existing = previous.indexOfFirst { it.start == left && it.end == right }
        if (existing >= 0) segments[existing]
        else BunsetsuSegmentState(input.substring(left, right), input.substring(left, right))
    }
}

internal fun moveSegment(segments: List<BunsetsuSegmentState>, from: Int, to: Int): List<BunsetsuSegmentState> {
    require(from in segments.indices && to in segments.indices)
    return segments.toMutableList().apply { add(to, removeAt(from)) }
}

/** Preserve unaffected manual boundaries through one reading edit. */
internal fun remapSegmentBoundaries(old: String, new: String, boundaries: List<Int>): List<Int> {
    if (old == new) return boundaries
    val oldLegal = readingBoundaries(old).toSet()
    val newLegal = readingBoundaries(new).toSet()
    var start = 0
    while (start < old.length && start < new.length && old[start] == new[start]) start++
    while (start !in oldLegal || start !in newLegal) start--
    var oldEnd = old.length
    var newEnd = new.length
    while (oldEnd > start && newEnd > start && old[oldEnd - 1] == new[newEnd - 1]) {
        oldEnd--
        newEnd--
    }
    while ((oldEnd !in oldLegal || newEnd !in newLegal) && oldEnd < old.length && newEnd < new.length) {
        oldEnd++
        newEnd++
    }
    val insertion = oldEnd == start
    val delta = newEnd - oldEnd
    return boundaries.mapNotNull { boundary ->
        when {
            boundary < start -> boundary
            insertion && boundary == start -> boundary + delta
            boundary <= start -> boundary
            boundary >= oldEnd -> boundary + delta
            else -> null
        }
    }.filter { it in 1 until new.length && it in newLegal }.distinct().sorted()
}
