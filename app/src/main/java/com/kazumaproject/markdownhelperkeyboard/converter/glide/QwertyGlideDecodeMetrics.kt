package com.kazumaproject.markdownhelperkeyboard.converter.glide

data class QwertyGlideDecodeMetrics(
    val dictionaryReady: Boolean,
    val rawBucketCandidateCount: Int,
    val prefilterCandidateCount: Int,
    val fullScoreCandidateCount: Int,
    val rerankCandidateCount: Int,
    val decodeTotalMs: Long,
    val prefilterMs: Long,
    val fullScoreMs: Long,
    val rerankMs: Long,
    val cacheHit: Boolean
)
