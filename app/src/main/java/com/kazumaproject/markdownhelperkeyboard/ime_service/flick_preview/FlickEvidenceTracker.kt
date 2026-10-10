package com.kazumaproject.markdownhelperkeyboard.ime_service.flick_preview

import com.kazumaproject.core.domain.flick.FlickInputEvidence
import com.kazumaproject.markdownhelperkeyboard.converter.graph.FlickCorrectionInput
import com.kazumaproject.markdownhelperkeyboard.converter.graph.KanaFlickLayout

/** Tracks only the current composing head. Unexplained edits invalidate their affected range. */
internal class FlickEvidenceTracker {
    private var input = ""
    private var evidence = mutableListOf<FlickInputEvidence?>()

    @Synchronized
    fun clear() {
        input = ""
        evidence.clear()
    }

    @Synchronized
    fun record(before: String, after: String, sample: FlickInputEvidence?) {
        synchronize(before)
        synchronize(after)
        // A gesture that cycles/replaces an existing character is deliberate toggle input.
        if (sample != null && after == before + sample.observedChar) {
            evidence[after.lastIndex] = sample
        }
    }

    @Synchronized
    fun snapshot(current: String): FlickCorrectionInput {
        // Background queries must never rewind the tracker to an obsolete input snapshot.
        return FlickCorrectionInput(current, if (current == input) evidence.toList() else emptyList())
    }

    private fun synchronize(current: String) {
        if (input == current) return
        if (current.isEmpty()) {
            clear()
            return
        }
        // Only predictable append/backspace or a modifier on the last kana preserves evidence.
        when {
            current.startsWith(input) -> repeat(current.length - input.length) { evidence.add(null) }
            input.startsWith(current) -> evidence.subList(current.length, evidence.size).clear()
            current.length == input.length && current.dropLast(1) == input.dropLast(1) -> {
                evidence[current.lastIndex] = if (KanaFlickLayout.baseChar(current.last()) == KanaFlickLayout.baseChar(input.last()))
                    evidence.lastOrNull()?.copy(observedChar = current.last()) else null
            }
            else -> evidence = MutableList(current.length) { null }
        }
        input = current
    }
}
