package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.core.data.floating_candidate.CandidateInputRange

/**
 * Presentation metadata for one candidate returned by one conversion query.
 *
 * The candidate's source range is kept next to the candidate object.  It is deliberately not
 * inferred from the output length: Japanese conversion can change both the number of characters
 * and the number of UTF-16 code units between input and output.
 */
data class CandidatePresentation(
    val candidate: Candidate,
    /**
     * Source range in the query input. Null means that this candidate has no trustworthy source
     * range and must not be applied to an active composing region.
     */
    val inputRange: CandidateInputRange?,
    val conversionSegments: List<CandidateConversionSegment> = emptyList(),
)
