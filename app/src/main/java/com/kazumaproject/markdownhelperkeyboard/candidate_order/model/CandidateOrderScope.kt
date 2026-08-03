package com.kazumaproject.markdownhelperkeyboard.candidate_order.model

enum class CandidateOrderScope {
    EXACT_INPUT,
    LEXICAL_UNIT;

    companion object {
        fun fromDatabase(value: String): CandidateOrderScope =
            entries.firstOrNull { it.name == value } ?: EXACT_INPUT
    }
}
