package com.kazumaproject.markdownhelperkeyboard.candidate_order.model

data class SavedCandidateOrderGroup(
    val input: String,
    val candidates: List<String>,
    val updatedAt: Long
)
