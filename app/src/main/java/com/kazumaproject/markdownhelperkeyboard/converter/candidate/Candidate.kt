package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.utility.FormulaCandidatePresentation

/**
 * @see 1:NBest 2:Part of letters 3:Hirakana 4:Katakana 5:Combine part of letter 6. Single Kanji
 **/
data class Candidate(
    val string: String,
    val type: Byte,
    val length: UByte,
    val score: Int,
    val yomi: String? = null,
    val leftId: Short? = null,
    val rightId: Short? = null,
    /** Stable source identity for action candidates whose display string must never be committed. */
    val sourceId: Long? = null,
    /** Text sent to InputConnection. Defaults to the legacy candidate string. */
    val commitText: String = string,
    /** Proof for generated numeric variants, retained by copy/de-duplication. */
    val number: com.kazumaproject.markdownhelperkeyboard.converter.engine.ValidatedNumber? = null,
    val generatedNumber: Boolean = number != null,
    /** Original reading and exact output of the Calendar/era generator (never learned data). */
    val temporalSource: Pair<String, String>? = null,
    /** Exact node provenance, including explicit user dictionary segments. */
    val conversionSegments: List<CandidateConversionSegment> = emptyList(),
    /** Optional non-text presentation, currently used by formula candidates. */
    val presentation: FormulaCandidatePresentation? = null,
)
