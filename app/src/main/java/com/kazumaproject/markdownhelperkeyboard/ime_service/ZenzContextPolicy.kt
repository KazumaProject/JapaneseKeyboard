package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

internal data class ResolvedZenzContext(
    val leftContext: String,
    val rightContext: String
)

internal fun resolveZenzContext(
    leftContext: String,
    rawRightContext: String,
    enableRightContext: Boolean
): ResolvedZenzContext {
    return ResolvedZenzContext(
        leftContext = leftContext,
        rightContext = if (enableRightContext) rawRightContext else ""
    )
}

internal fun buildZenzRerankCacheKey(
    profile: String,
    leftContext: String,
    rightContext: String,
    input: String,
    rerankTargets: List<IndexedValue<Candidate>>
): String {
    return buildString {
        append(profile)
        append('\u0001')
        append(leftContext)
        append('\u0001')
        append(rightContext)
        append('\u0001')
        append(input)
        rerankTargets.forEach {
            append('\u0002')
            append(it.index)
            append('\u0003')
            append(it.value.string)
            append('\u0003')
            append(it.value.score)
        }
    }
}
