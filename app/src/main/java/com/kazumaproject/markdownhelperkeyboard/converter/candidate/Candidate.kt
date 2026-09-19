package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.utility.FormulaCandidatePresentation

/**
 * @see 1:NBest 2:Part of letters 3:Hirakana 4:Katakana 5:Combine part of letter 6. Single Kanji
 **/
data class Candidate(
    val string: String,
    val type: Byte,
    val length: Int,
    val score: Int,
    val yomi: String? = null,
    val leftId: Short? = null,
    val rightId: Short? = null,
    /** Stable source identity for action candidates whose display string must never be committed. */
    val sourceId: Long? = null,
    /** Text sent to InputConnection. Defaults to the legacy candidate string. */
    val commitText: String = string,
    /** Optional non-text presentation, currently used by formula candidates. */
    val presentation: FormulaCandidatePresentation? = null,
    /** Exact path segments, retained independently from the display string. */
    val conversionSegments: List<CandidateConversionSegment> = emptyList(),
    /** Query-local identity of the linguistic interpretation represented by this item. */
    val interpretationId: Long? = null,
    /** Interpretation inherited by a display-only numeric variant. */
    val derivedFromInterpretationId: Long? = null,
    val numericRole: NumericCandidateRole = NumericCandidateRole.NONE,
    val numberSpans: List<NumberCandidateSpan> = emptyList(),
    /** False for post-ranking notation variants and parser-only completion candidates. */
    val rankingEligible: Boolean = true,
)

enum class NumericCandidateRole {
    NONE,
    REPRESENTATIVE,
    VARIANT,
    SUPPLEMENT,
}

/** Half-open input/output ranges proven by both the reading parser and the selected path. */
data class NumberCandidateSpan(
    val inputStart: Int,
    val inputEnd: Int,
    val outputStart: Int,
    val outputEnd: Int,
    val forms: List<String>,
    val allowedForms: Set<Int>,
    val normalizedDigits: String,
    val unitId: String,
)
