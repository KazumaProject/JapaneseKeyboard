package com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry

import android.icu.text.BreakIterator
import java.util.Locale

/** Add display-only line breaks without splitting emoji or combining characters. */
private fun verticalCandidateGraphemes(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
    val parts = ArrayList<String>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        parts.add(text.substring(start, end))
        start = end
        end = iterator.next()
    }
    return parts
}

internal fun verticalCandidateText(text: String): String = verticalCandidateGraphemes(text).joinToString("\n")

/** Split one candidate into successive display columns without changing its text. */
internal fun verticalCandidateColumns(text: String, maxLines: Int): List<String> =
    verticalCandidateGraphemes(text).map { grapheme ->
        // A literal line break is one visible row, not an extra uncounted TextView line.
        when (grapheme) { "\n", "\r", "\r\n" -> "↵"; "\t" -> "⇥"; else -> grapheme }
    }.chunked(maxLines.coerceAtLeast(1)).map { it.joinToString("\n") }

/** Truncate only the presentation; the candidate's underlying text is never changed. */
internal fun ellipsizeVerticalLines(text: String, maxLines: Int): String {
    val limit = maxLines.coerceAtLeast(1)
    val lines = text.split('\n')
    return when {
        lines.size <= limit -> text
        limit == 1 -> lines.first() + "…"
        else -> (lines.take(limit - 1) + "…").joinToString("\n")
    }
}
