package com.kazumaproject.markdownhelperkeyboard.converter.utility

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_CALCULATION
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_UNIT_CONVERSION
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_UTILITY_LITERAL
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

object UtilityCandidateComposer {
    fun compose(
        input: String,
        existingCandidates: List<Candidate>,
        result: UtilityCandidateResult,
    ): List<Candidate> {
        if (!result.hasCandidates) return existingCandidates
        val utilityCandidates = result.candidates.map { candidate ->
            Candidate(
                string = candidate.text,
                type = when (candidate.kind) {
                    UtilityCandidateKind.CALCULATION -> CANDIDATE_TYPE_CALCULATION
                    UtilityCandidateKind.UNIT_CONVERSION -> CANDIDATE_TYPE_UNIT_CONVERSION
                    UtilityCandidateKind.LITERAL -> CANDIDATE_TYPE_UTILITY_LITERAL
                },
                length = input.length.coerceAtMost(UByte.MAX_VALUE.toInt()).toUByte(),
                score = 0,
                yomi = input,
            )
        }
        val ordered = when (result.trigger) {
            UtilityTrigger.EXPLICIT_CALCULATION,
            UtilityTrigger.EXPLICIT_UNIT_CONVERSION -> utilityCandidates + existingCandidates

            UtilityTrigger.AUTOMATIC_UNIT_CONVERSION -> {
                val literal = existingCandidates.firstOrNull { it.string == input } ?: Candidate(
                    string = input,
                    type = CANDIDATE_TYPE_UTILITY_LITERAL,
                    length = input.length.coerceAtMost(UByte.MAX_VALUE.toInt()).toUByte(),
                    score = 0,
                    yomi = input,
                )
                listOf(literal) + utilityCandidates + existingCandidates.filterNot { it === literal }
            }

            UtilityTrigger.NONE -> existingCandidates
        }
        return ordered.distinctBy(Candidate::string)
    }

    fun isUtilityCandidate(candidate: Candidate?): Boolean = when (candidate?.type) {
        CANDIDATE_TYPE_CALCULATION,
        CANDIDATE_TYPE_UNIT_CONVERSION,
        CANDIDATE_TYPE_UTILITY_LITERAL -> true
        else -> false
    }

    fun isResultCandidate(candidate: Candidate?): Boolean = when (candidate?.type) {
        CANDIDATE_TYPE_CALCULATION,
        CANDIDATE_TYPE_UNIT_CONVERSION -> true
        else -> false
    }
}
