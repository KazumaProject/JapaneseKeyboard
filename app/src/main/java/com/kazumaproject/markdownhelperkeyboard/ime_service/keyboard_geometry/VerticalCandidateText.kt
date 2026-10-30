package com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry

import android.icu.text.BreakIterator
import android.os.Build
import android.text.StaticLayout
import android.widget.TextView
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

/** Account for fallback fonts, whose actual line height can exceed TextView.lineHeight. */
internal fun verticalCandidateMaxLines(text: TextView, formatted: String, availableHeight: Int): Int {
    if (formatted.isEmpty()) return 1
    val builder = StaticLayout.Builder.obtain(formatted, 0, formatted.length, text.paint, 1_000_000)
        .setIncludePad(text.includeFontPadding)
        .setLineSpacing(text.lineSpacingExtra, text.lineSpacingMultiplier)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) builder.setUseLineSpacingFromFallbacks(true)
    val layout = builder.build()
    val renderedLineHeight = (0 until layout.lineCount).maxOfOrNull {
        layout.getLineBottom(it) - layout.getLineTop(it)
    } ?: 0
    val lineHeight = maxOf(text.lineHeight, renderedLineHeight, 1)
    val contentHeight = (availableHeight - text.compoundPaddingTop - text.compoundPaddingBottom).coerceAtLeast(1)
    // Leave a line for font padding and other decorations in the candidate item.
    return (contentHeight / lineHeight - 1).coerceAtLeast(1)
}

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
