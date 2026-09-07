package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_LEARNED_DICTIONARY
import java.util.Collections
import java.util.IdentityHashMap

/** Keeps lexical and learned candidates intact when adding generated notation alternatives. */
internal object NumericCandidateComposer {
    fun compose(
        input: String,
        candidates: List<Candidate>,
        numericCandidates: List<Candidate>,
    ): List<Candidate> {
        if (numericCandidates.isEmpty()) return candidates
        // Equality of values does not imply that a dictionary entry came from the renderer.
        val generated = Collections.newSetFromMap(IdentityHashMap<Candidate, Boolean>())
        generated.addAll(numericCandidates)
        val lexical = candidates.filterNot { generated.contains(it) }
        if (!NumericCandidateProvider.shouldPrioritize(input)) {
            return (lexical + numericCandidates).distinctBy { it.string }
        }
        val lexicalBySurface = lexical.distinctBy { it.string }.associateBy { it.string }
        val learned = lexical.filter { it.type == CANDIDATE_TYPE_LEARNED_DICTIONARY }
        val orderedNumeric = numericCandidates.map { lexicalBySurface[it.string] ?: it }
        return (learned + orderedNumeric + lexical).distinctBy { it.string }
    }
}
