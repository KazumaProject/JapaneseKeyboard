package com.kazumaproject.markdownhelperkeyboard.converter.candidate

/**
 * Conversion metadata attached to one concrete candidate instance.
 *
 * The association is intentionally object/position based at the query boundary. The conversion
 * output string is not a stable key: two candidates can display the same string while carrying
 * different source-path information, and asynchronous candidate reordering must not change the
 * range used by a candidate row.
 */
data class CandidateConversionMetadata(
    val candidate: Candidate,
    val conversionSegments: List<CandidateConversionSegment>,
)
