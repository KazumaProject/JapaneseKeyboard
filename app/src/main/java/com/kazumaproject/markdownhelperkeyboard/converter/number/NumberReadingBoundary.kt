package com.kazumaproject.markdownhelperkeyboard.converter.number

/** Particle-aware boundaries used by the conservative kana number and counter scanners. */
internal object NumberReadingBoundary {
    private const val SINGLE_CHARACTER_BOUNDARIES = "はをがにでとへものや、。！？,. "
    private val particles = listOf(
        "から", "まで", "より", "くらい", "ぐらい", "ほど", "だけ", "しか", "でも",
        "とも", "など", "には", "では", "とは", "へは", "にも", "からは", "までは",
    )

    fun isStartBoundary(input: String, start: Int): Boolean =
        start == 0 ||
            input[start - 1] in SINGLE_CHARACTER_BOUNDARIES ||
            particles.any { particle ->
                val boundaryStart = start - particle.length
                boundaryStart >= 0 && input.regionMatches(boundaryStart, particle, 0, particle.length)
            }

    fun isEndBoundary(input: String, end: Int): Boolean =
        end == input.length ||
            input[end] in SINGLE_CHARACTER_BOUNDARIES ||
            particles.any { particle -> input.regionMatches(end, particle, 0, particle.length) }

    fun isBoundary(input: String, start: Int, end: Int): Boolean =
        isStartBoundary(input, start) && isEndBoundary(input, end)
}
