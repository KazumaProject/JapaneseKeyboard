package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.core.domain.extensions.isFullWidthNumericSymbol
import com.kazumaproject.core.domain.extensions.isHalfWidthNumericSymbol

// Legacy behavior/source IDs, not an exhaustive classification of the output characters.
// Keep their values stable: commit, learning and action handling depend on these IDs.
const val CANDIDATE_TYPE_CONVERSION: Byte = 1
const val CANDIDATE_TYPE_EXACT_DICTIONARY: Byte = 2
const val CANDIDATE_TYPE_KANA_LITERAL: Byte = 3
const val CANDIDATE_TYPE_KATAKANA: Byte = 4
const val CANDIDATE_TYPE_PARTIAL: Byte = 5
const val CANDIDATE_TYPE_SINGLE_KANJI: Byte = 7
const val CANDIDATE_TYPE_PREDICTION: Byte = 9 // Also used by zero-query candidates.
const val CANDIDATE_TYPE_EMOJI: Byte = 11
const val CANDIDATE_TYPE_EMOTICON: Byte = 12
const val CANDIDATE_TYPE_SYMBOL: Byte = 13
const val CANDIDATE_TYPE_DATE: Byte = 14
const val CANDIDATE_TYPE_READING_CORRECTION: Byte = 15
const val CANDIDATE_TYPE_PROVERB: Byte = 16
const val CANDIDATE_TYPE_NUMBER_WITH_UNITS: Byte = 17
const val CANDIDATE_TYPE_NUMBER_LITERAL: Byte = 18
const val CANDIDATE_TYPE_NUMBER_GROUPED: Byte = 19
const val CANDIDATE_TYPE_NUMBER_SCRIPT: Byte = 20 // Subscript or exponent notation.
const val CANDIDATE_TYPE_NUMBER_SYMBOL: Byte = 21 // Includes superscript and numeric symbols.
const val CANDIDATE_TYPE_FULL_WIDTH_NUMBER: Byte = 22
const val CANDIDATE_TYPE_NUMBER_LARGE_UNITS: Byte = 23
const val CANDIDATE_TYPE_USER_DICTIONARY: Byte = 28
const val CANDIDATE_TYPE_ENGLISH: Byte = 29
const val CANDIDATE_TYPE_FULL_WIDTH: Byte = 30
const val CANDIDATE_TYPE_HALF_WIDTH: Byte = 31
const val CANDIDATE_TYPE_KANJI_NUMBER: Byte = 32
const val CANDIDATE_TYPE_ZENZ: Byte = 33
const val CANDIDATE_TYPE_LEARNED_DICTIONARY: Byte = 34
const val CANDIDATE_TYPE_ENGLISH_CORRECTION: Byte = 35
const val CANDIDATE_TYPE_ZENZ_PASS: Byte = 36
const val CANDIDATE_TYPE_ZENZ_LOCAL_FALLBACK: Byte = 37
const val CANDIDATE_TYPE_ZENZ_WHOLE_RESULT: Byte = 38
const val CANDIDATE_TYPE_ZENZ_ERROR_FALLBACK: Byte = 39
const val CANDIDATE_TYPE_ZENZ_REGENERATED: Byte = 40
// 41..44 are Gemma result/action IDs; 6, 8, 10 and 24..27 are legacy reserved IDs.
const val QWERTY_GLIDE_CANDIDATE_TYPE: Byte = 45
const val CANDIDATE_TYPE_USER_TEMPLATE: Byte = 46
const val CANDIDATE_TYPE_TIME: Byte = 47
const val CANDIDATE_TYPE_ERA: Byte = 48
const val CANDIDATE_TYPE_CALCULATION: Byte = 49
const val CANDIDATE_TYPE_UNIT_CONVERSION: Byte = 50
const val CANDIDATE_TYPE_UTILITY_LITERAL: Byte = 51
const val CANDIDATE_TYPE_TEXT_MACRO: Byte = 52
const val CANDIDATE_TYPE_FORMULA_UNICODE: Byte = 53
const val CANDIDATE_TYPE_FORMULA_TEX: Byte = 54
// Separate from Zenz's 40. Deliberately retains the legacy length-based commit/learning path.
const val CANDIDATE_TYPE_DIGIT_DATE: Byte = 55
const val CANDIDATE_TYPE_HALF_WIDTH_KATAKANA: Byte = 56
// Preserve the source-specific commit behavior of types 13 and 21 while separating widths.
const val CANDIDATE_TYPE_FULL_WIDTH_SYMBOL: Byte = 57
const val CANDIDATE_TYPE_HALF_WIDTH_SYMBOL: Byte = 58
const val CANDIDATE_TYPE_FULL_WIDTH_NUMBER_SYMBOL: Byte = 59
const val CANDIDATE_TYPE_HALF_WIDTH_NUMBER_SYMBOL: Byte = 60

/** Classify symbol width at generation time, keeping all other symbol forms on their source ID. */
internal fun Candidate.withSymbolWidthType(): Candidate {
    if ((type != CANDIDATE_TYPE_SYMBOL && type != CANDIDATE_TYPE_NUMBER_SYMBOL) || string.isEmpty()) {
        return this
    }
    val fullWidth = string.all { it.isFullWidthNumericSymbol() }
    val halfWidth = string.all { it.isHalfWidthNumericSymbol() }
    val resultType = when (type) {
        CANDIDATE_TYPE_SYMBOL -> when {
            fullWidth -> CANDIDATE_TYPE_FULL_WIDTH_SYMBOL
            halfWidth -> CANDIDATE_TYPE_HALF_WIDTH_SYMBOL
            else -> type
        }
        else -> when {
            fullWidth -> CANDIDATE_TYPE_FULL_WIDTH_NUMBER_SYMBOL
            halfWidth -> CANDIDATE_TYPE_HALF_WIDTH_NUMBER_SYMBOL
            else -> type
        }
    }
    return if (resultType == type) this else copy(type = resultType)
}
