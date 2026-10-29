package com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry

import android.icu.text.BreakIterator
import java.util.Locale

/** Add display-only line breaks without splitting emoji or combining characters. */
internal fun verticalCandidateText(text: String): String {
    if (text.isEmpty()) return text
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
    val parts = ArrayList<String>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        parts.add(text.substring(start, end))
        start = end
        end = iterator.next()
    }
    return parts.joinToString("\n")
}
