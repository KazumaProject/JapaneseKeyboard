package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.core.domain.extensions.toHankakuAlphabet
import com.kazumaproject.core.domain.extensions.toZenkakuAlphabet

internal fun buildRomajiCandidates(
    readingLength: Int,
    romaji: String
): List<Candidate> {
    val halfWidth = romaji.toHankakuAlphabet()
    val fullWidth = halfWidth.toZenkakuAlphabet()
    val halfWidthCapitalized = halfWidth.replaceFirstChar { it.uppercaseChar() }
    val fullWidthCapitalized = halfWidthCapitalized.toZenkakuAlphabet()
    val halfWidthUppercase = halfWidth.uppercase()
    val fullWidthUppercase = halfWidthUppercase.toZenkakuAlphabet()

    return listOf(
        Candidate(
            string = fullWidth,
            type = 30,
            length = readingLength.toUByte(),
            score = 29000
        ),
        Candidate(
            string = halfWidth,
            type = 31,
            length = readingLength.toUByte(),
            score = 29001
        ),
        Candidate(
            string = fullWidthCapitalized,
            type = 30,
            length = readingLength.toUByte(),
            score = 29002
        ),
        Candidate(
            string = halfWidthCapitalized,
            type = 31,
            length = readingLength.toUByte(),
            score = 29003
        ),
        Candidate(
            string = fullWidthUppercase,
            type = 30,
            length = readingLength.toUByte(),
            score = 29004
        ),
        Candidate(
            string = halfWidthUppercase,
            type = 31,
            length = readingLength.toUByte(),
            score = 29005
        )
    )
}
