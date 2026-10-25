package com.kazumaproject.markdownhelperkeyboard.gesture

import com.kazumaproject.gesture_keyboard.*

/** A matching variant of the SAME output must not mask a conflicting DIFFERENT output. */
internal fun gestureConflicts(set: GestureSet, entry: GestureEntry, maxDistance: Float): List<GestureRecognizer.Match> {
    val matcher = GestureRecognizer.Prepared(set.copy(entries = set.entries.filter {
        it.id != entry.id && it.output != entry.output
    }))
    return entry.samples.mapNotNull { sample ->
        matcher.ranked(sample).firstOrNull()?.takeIf { it.distance < maxDistance }
    }.distinctBy { it.entry.output }
}
