package com.kazumaproject.core.data.floating_candidate

data class CandidateItem(
    val word: String,
    val length: UByte,
    val candidateType: Byte = 0,
    val sourceId: Long? = null,
)
