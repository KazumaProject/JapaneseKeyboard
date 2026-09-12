package com.kazumaproject.markdownhelperkeyboard.converter.engine

enum class NumberCandidateOrder(val preferenceValue: String, val indices: List<Int>) {
    HALF_FULL_KANJI("half_full_kanji", listOf(0, 1, 2)),
    HALF_KANJI_FULL("half_kanji_full", listOf(0, 2, 1)),
    FULL_HALF_KANJI("full_half_kanji", listOf(1, 0, 2)),
    FULL_KANJI_HALF("full_kanji_half", listOf(1, 2, 0)),
    KANJI_HALF_FULL("kanji_half_full", listOf(2, 0, 1)),
    KANJI_FULL_HALF("kanji_full_half", listOf(2, 1, 0));

    companion object {
        fun fromPreference(value: String?): NumberCandidateOrder =
            entries.firstOrNull { it.preferenceValue == value } ?: HALF_FULL_KANJI
    }
}
