package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.graph.FlickCorrectionInfo
import com.kazumaproject.markdownhelperkeyboard.converter.utility.FormulaCandidatePresentation
import com.kazumaproject.markdownhelperkeyboard.converter.date.DateCandidateFormat

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
    /** Optional non-text presentation, currently used by formula candidates. */
    val presentation: FormulaCandidatePresentation? = null,
    /** Exact conversion path used only to align live candidate readings. */
    val conversionSegments: List<CandidateConversionSegment> = emptyList(),
    /** Date format identity for daily date candidates that can be reordered or disabled. */
    val dateFormat: DateCandidateFormat? = null,
    val flickCorrection: FlickCorrectionInfo? = null,
)
