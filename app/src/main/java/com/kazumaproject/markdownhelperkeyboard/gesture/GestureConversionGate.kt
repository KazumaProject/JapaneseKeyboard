package com.kazumaproject.markdownhelperkeyboard.gesture

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Never preview candidates belonging to a previous stroke's reading. */
internal suspend fun awaitGestureCandidates(
    reading: String,
    input: StateFlow<String>,
    results: StateFlow<Pair<String, List<Candidate>>?>
): List<Candidate>? = withTimeoutOrNull(2_000) {
    val (result, current) = combine(results, input) { result, current -> result to current }
        .first { (result, current) -> current != reading || result?.first == reading }
    result?.second?.takeIf { current == reading && result.first == reading }
}
