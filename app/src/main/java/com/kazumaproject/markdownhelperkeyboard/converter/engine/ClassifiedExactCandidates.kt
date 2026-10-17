package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory

/** Whole-input dictionary matches are independent of lattice and completion limits. */
internal object ClassifiedExactCandidates {
    fun append(
        input: String,
        candidates: List<Candidate>,
        dictionaries: Map<DictionaryCategory, TripleDictionaryData>,
        enabledCategories: Set<DictionaryCategory>,
        candidateSegmentCollector: MutableMap<String, List<CandidateConversionSegment>>? = null,
    ): List<Candidate> {
        if (input.isEmpty() || enabledCategories.isEmpty() || dictionaries.isEmpty()) return candidates
        val reading = input.toCharArray()
        var matches: LinkedHashMap<String, Candidate>? = null
        for ((category, data) in dictionaries) {
            if (category !in enabledCategories) continue
            val node = data.yomiTrie.getNodeIndex(reading, data.succinctBitVectorLBSYomi)
            if (node <= 0) continue
            val term = data.yomiTrie.getTermId(node, data.succinctBitVectorIsLeafYomi)
            if (term < 0) continue
            val categoryLabels = setOf(category)
            for (token in data.tokenArray.getListDictionaryByYomiTermId(term, data.succinctBitVectorTokenArray)) {
                val surface = data.surfaceFor(token.nodeId, input, token.posTableIndex.toInt(), token.wordCost)
                val collected = matches ?: LinkedHashMap<String, Candidate>().also { matches = it }
                val previous = collected[surface]
                val labels = previous?.dictionaryCategories?.plus(category) ?: categoryLabels
                if (previous == null || token.wordCost.toInt() < previous.score) {
                    collected[surface] = Candidate(
                        string = surface, type = 1, length = input.length.toUByte(),
                        score = token.wordCost.toInt(), yomi = input,
                        leftId = data.tokenArray.leftIds[token.posTableIndex.toInt()],
                        rightId = data.tokenArray.rightIds[token.posTableIndex.toInt()],
                        dictionaryCategories = labels,
                    )
                } else if (labels != previous.dictionaryCategories) {
                    collected[surface] = previous.copy(dictionaryCategories = labels)
                }
            }
        }
        val remaining = matches ?: return candidates
        var result: MutableList<Candidate>? = null
        for ((index, candidate) in candidates.withIndex()) {
            val exact = remaining[candidate.string] ?: continue
            // Action/presentation candidates with the same label do not represent this word.
            if (candidate.sourceId != null || candidate.presentation != null || candidate.commitText != exact.commitText) continue
            remaining.remove(candidate.string)
            if (!candidate.dictionaryCategories.containsAll(exact.dictionaryCategories)) {
                val updated = result ?: candidates.toMutableList().also { result = it }
                updated[index] = candidate.copy(dictionaryCategories = candidate.dictionaryCategories + exact.dictionaryCategories)
            }
        }
        if (remaining.isEmpty()) return result ?: candidates
        val updated = result ?: ArrayList<Candidate>(candidates.size + remaining.size).also { it.addAll(candidates) }
        for (candidate in remaining.values.sortedWith(compareBy<Candidate> { it.score }.thenBy { it.string })) {
            updated.add(candidate)
            candidateSegmentCollector?.put(
                candidate.string, listOf(CandidateConversionSegment(0, input.length, candidate.string)),
            )
        }
        return updated
    }

    fun append(
        input: String,
        result: BunsetsuCandidateResult,
        dictionaries: Map<DictionaryCategory, TripleDictionaryData>,
        enabledCategories: Set<DictionaryCategory>,
        candidateSegmentCollector: MutableMap<String, List<CandidateConversionSegment>>? = null,
    ): BunsetsuCandidateResult {
        val candidates = append(input, result.candidates, dictionaries, enabledCategories, candidateSegmentCollector)
        if (candidates === result.candidates) return result
        val patterns = if (candidates.size == result.candidates.size) result.splitPatternByCandidateString else {
            result.splitPatternByCandidateString.toMutableMap().apply {
                for (index in result.candidates.size until candidates.size) put(candidates[index].string, emptyList())
            }
        }
        return result.copy(candidates = candidates, splitPatternByCandidateString = patterns)
    }
}
