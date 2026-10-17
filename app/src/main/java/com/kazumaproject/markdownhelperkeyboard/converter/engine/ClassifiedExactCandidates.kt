package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.FindPath.ScoredWholeInputCandidate
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory

/** Whole-input matches compete in the main conversion group, independently of search limits. */
internal object ClassifiedExactCandidates {
    private data class MergeResult(
        val candidates: List<Candidate>,
        val wholeInputSurfaces: Set<String> = emptySet(),
        val matched: Set<String> = emptySet(),
    )

    fun merge(
        input: String,
        candidates: List<Candidate>,
        dictionaries: Map<DictionaryCategory, TripleDictionaryData>,
        enabledCategories: Set<DictionaryCategory>,
        scoreCandidate: (Candidate) -> ScoredWholeInputCandidate,
        candidateSegmentCollector: MutableMap<String, List<CandidateConversionSegment>>? = null,
        primaryCandidateCount: Int = candidates.size,
    ): List<Candidate> = mergeCandidates(input, candidates, dictionaries, enabledCategories,
        scoreCandidate, candidateSegmentCollector, primaryCandidateCount, emptySet()).candidates

    fun merge(
        input: String,
        result: BunsetsuCandidateResult,
        dictionaries: Map<DictionaryCategory, TripleDictionaryData>,
        enabledCategories: Set<DictionaryCategory>,
        scoreCandidate: (Candidate) -> ScoredWholeInputCandidate,
        candidateSegmentCollector: MutableMap<String, List<CandidateConversionSegment>>? = null,
        primaryCandidateCount: Int = result.candidates.size,
    ): BunsetsuCandidateResult {
        val merged = mergeCandidates(input, result.candidates, dictionaries, enabledCategories,
            scoreCandidate, candidateSegmentCollector, primaryCandidateCount, result.systemNgramMatchedCandidates)
        if (merged.candidates === result.candidates) return result
        val patterns = if (merged.wholeInputSurfaces.isEmpty()) result.splitPatternByCandidateString else {
            result.splitPatternByCandidateString.toMutableMap().apply {
                merged.wholeInputSurfaces.forEach { put(it, emptyList()) }
            }
        }
        return result.copy(candidates = merged.candidates, splitPatternByCandidateString = patterns,
            systemNgramMatchedCandidates = result.systemNgramMatchedCandidates + merged.matched)
    }

    private fun mergeCandidates(
        input: String,
        candidates: List<Candidate>,
        dictionaries: Map<DictionaryCategory, TripleDictionaryData>,
        enabledCategories: Set<DictionaryCategory>,
        scoreCandidate: (Candidate) -> ScoredWholeInputCandidate,
        candidateSegmentCollector: MutableMap<String, List<CandidateConversionSegment>>?,
        primaryCandidateCount: Int,
        systemNgramMatched: Set<String>,
    ): MergeResult {
        if (input.isEmpty() || enabledCategories.isEmpty() || dictionaries.isEmpty()) return MergeResult(candidates)
        val reading = input.toCharArray()
        val matches = LinkedHashMap<String, Candidate>()
        val matched = systemNgramMatched.toMutableSet()
        val selectedNodeMatches = HashSet<String>()
        for ((category, data) in dictionaries) {
            if (category !in enabledCategories) continue
            val node = data.yomiTrie.getNodeIndex(reading, data.succinctBitVectorLBSYomi)
            if (node <= 0) continue
            val term = data.yomiTrie.getTermId(node, data.succinctBitVectorIsLeafYomi)
            if (term < 0) continue
            val labels = setOf(category)
            for (token in data.tokenArray.getListDictionaryByYomiTermId(term, data.succinctBitVectorTokenArray)) {
                val pos = token.posTableIndex.toInt()
                val surface = data.surfaceFor(token.nodeId, input, pos, token.wordCost)
                val scored = scoreCandidate(Candidate(
                    string = surface, type = 1, length = input.length.toUByte(),
                    score = token.wordCost.toInt(), yomi = input,
                    leftId = data.tokenArray.leftIds[pos], rightId = data.tokenArray.rightIds[pos],
                    dictionaryCategories = labels,
                ))
                val previous = matches[surface]
                val categories = previous?.dictionaryCategories?.plus(category) ?: labels
                val useScored = previous == null || scored.candidate.score < previous.score
                if (useScored) {
                    if (scored.systemNgramMatched) selectedNodeMatches.add(surface) else selectedNodeMatches.remove(surface)
                }
                val winner = if (useScored) scored.candidate else requireNotNull(previous)
                val standalone = previous?.standaloneUnigramMatched == true || scored.candidate.standaloneUnigramMatched
                if (standalone) selectedNodeMatches.add(surface)
                matches[surface] = if (winner.dictionaryCategories == categories && winner.standaloneUnigramMatched == standalone) winner
                    else winner.copy(dictionaryCategories = categories, standaloneUnigramMatched = standalone)
            }
        }
        if (matches.isEmpty()) return MergeResult(candidates)
        matched.addAll(selectedNodeMatches)
        val comparator = compareByDescending<Candidate> { it.standaloneUnigramMatched || it.string in matched }
            .thenBy { it.score }.thenBy { it.string }
        val bestExisting = HashMap<String, Int>()
        for (index in 0 until primaryCandidateCount) {
            val candidate = candidates[index]
            if (candidate.string !in matches || candidate.sourceId != null || candidate.presentation != null ||
                candidate.commitText != candidate.string || candidate.length.toInt() != input.length ||
                (candidate.yomi != null && candidate.yomi != input)) continue
            val previous = bestExisting[candidate.string]
            if (previous == null || comparator.compare(candidate, candidates[previous]) < 0) bestExisting[candidate.string] = index
        }
        val primary = ArrayList<Candidate>(primaryCandidateCount + matches.size)
        primary.addAll(candidates.subList(0, primaryCandidateCount))
        val wholeInput = LinkedHashSet<String>()
        for ((surface, exact) in matches) {
            val index = bestExisting[surface]
            val existing = index?.let { candidates[it] }
            val labels = exact.dictionaryCategories + existing?.dictionaryCategories.orEmpty()
            if (existing != null && comparator.compare(existing, exact) <= 0) {
                val standalone = existing.standaloneUnigramMatched || exact.standaloneUnigramMatched
                if (existing.dictionaryCategories != labels || standalone != existing.standaloneUnigramMatched) {
                    primary[index] = existing.copy(dictionaryCategories = labels, standaloneUnigramMatched = standalone)
                }
            } else {
                val winner = if (labels == exact.dictionaryCategories) exact else exact.copy(dictionaryCategories = labels)
                if (index == null) primary.add(winner) else primary[index] = winner
                wholeInput.add(surface)
                candidateSegmentCollector?.put(surface, listOf(CandidateConversionSegment(0, input.length, surface)))
            }
        }
        primary.sortWith(comparator)
        primary.addAll(candidates.subList(primaryCandidateCount, candidates.size))
        return MergeResult(primary, wholeInput, matched)
    }
}
