package com.kazumaproject.markdownhelperkeyboard.converter.glide

data class QwertyGlideDecodeOptions(
    val maxResults: Int = 12,
    val beamWidth: Int = 128,
    val pointKeyTopK: Int = 5,
    val maxWordLength: Int = 24,
    val minWordLength: Int = 2,
    val minSamplingDistanceRatio: Float = 0.22f,
    val fullScoreCandidateLimit: Int = 384,
    val startEndRejectCost: Float = 2.6f,
    val startEndWeight: Float = 4.8f,
    val pathWeight: Float = 5.4f,
    val proximityWeight: Float = 1.7f,
    val lengthWeight: Float = 1.5f,
    val dictionaryWeight: Float = 0.00008f,
    val repeatedLetterWeight: Float = 0.45f
)
