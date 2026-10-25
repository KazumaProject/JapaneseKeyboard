package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.converter.utility.FormulaCandidatePresentation
import com.kazumaproject.markdownhelperkeyboard.converter.date.DateCandidateFormat

/**
 * [type] is a legacy behavior/source ID; see CandidateTypes.kt. Character forms are derived
 * from the displayed text, independently of this ID and of [dictionaryOrigins].
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
    /** Whole-input standalone unigram match with the original literal input. Never changes score. */
    val standaloneUnigramMatched: Boolean = false,
    val dictionaryCategories: Set<DictionaryCategory> = emptySet(),
    val dictionaryOrigins: Set<CandidateDictionaryOrigin> = dictionaryOriginsForType(type),
    val correctionMask: Int = 0,
)
