package com.kazumaproject.markdownhelperkeyboard.converter.number

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_LEARNED_DICTIONARY

/** Carries numeric meaning data onto a learned surface when it duplicates an engine path. */
object NumberCandidateMetadataInheritance {
    fun attachToLearnedDuplicates(candidates: List<Candidate>): List<Candidate> {
        val metadataBySurface = candidates
            .filter { it.numberMetadata != null }
            .groupBy(Candidate::string)
            .mapValues { (_, duplicates) ->
                duplicates.firstOrNull {
                    it.numberMetadata?.let { metadata ->
                        metadata.origin == NumberCandidateOrigin.SYSTEM_PATH && !metadata.isFallback
                    } == true
                }?.numberMetadata ?: duplicates.first().numberMetadata!!
            }

        return candidates.map { candidate ->
            if (
                candidate.type == CANDIDATE_TYPE_LEARNED_DICTIONARY &&
                candidate.numberMetadata == null
            ) {
                metadataBySurface[candidate.string]?.let { metadata ->
                    candidate.copy(
                        numberMetadata = metadata.copy(
                            origin = NumberCandidateOrigin.SYSTEM_PATH,
                            isFallback = false,
                        ),
                    )
                } ?: candidate
            } else {
                candidate
            }
        }
    }
}
