package com.kazumaproject.markdownhelperkeyboard.converter.mozc.model

class MozcSegment(
    var segmentType: MozcSegmentType = MozcSegmentType.FREE,
    var key: String = "",
    val candidates: MutableList<MozcCandidate> = mutableListOf(),
    val metaCandidates: MutableList<MozcCandidate> = mutableListOf(),
)

enum class MozcSegmentType {
    FREE,
    FIXED_BOUNDARY,
    FIXED_VALUE,
    SUBMITTED,
    HISTORY,
}

class MozcSegments {
    private val segments = mutableListOf<MozcSegment>()

    fun clear() = segments.clear()

    fun addConversionSegment(key: String): MozcSegment {
        val segment = MozcSegment(
            segmentType = MozcSegmentType.FREE,
            key = key,
        )
        segments.add(segment)
        return segment
    }

    fun conversionSegments(): List<MozcSegment> = segments

    fun firstConversionSegment(): MozcSegment? = segments.firstOrNull()
}
