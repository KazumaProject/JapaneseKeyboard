// Adapted from kotlin-kana-kanji-converter at dbbb97f (MIT). See the bundled English dictionary notices.
package com.kazumaproject.markdownhelperkeyboard.converter.english.phrase

import com.kazumaproject.markdownhelperkeyboard.converter.compact.CompactEnglishDictionary
import java.util.PriorityQueue

internal data class DictionaryCandidate(val reading: String, val surface: String, val cost: Int)

internal class EnglishPhraseDictionary(
    private val dictionary: CompactEnglishDictionary,
    val grammarRoles: GrammarRoles,
) {
    fun prefixes(text: String): List<String> =
        dictionary.readingTrie.commonPrefixSearch(text, dictionary.readingLbsIndex)

    fun exact(text: String): List<DictionaryCandidate> {
        val node = dictionary.readingTrie.getNodeIndex(text, dictionary.readingLbsIndex)
        if (node < 0 || !dictionary.readingTrie.isLeaf[node]) return emptyList()
        val term = dictionary.readingTrie.getTermId(node, dictionary.readingLeafIndex)
        return buildList {
            forEachCandidate(text, term) { candidate -> add(candidate) }
        }
    }

    fun complete(prefix: String, surfacePrefix: String?, limit: Int): List<DictionaryCandidate> {
        val best = PriorityQueue(completionOrder.reversed())
        val terms = dictionary.readingTrie.termsStartingWith(prefix, dictionary.readingLbsIndex, dictionary.readingLeafIndex)
        for ((key, term) in terms) {
            if (key == prefix || !completionKey.matches(key)) continue
            val maxCost = if (best.size == limit) best.element().cost else Int.MAX_VALUE
            forEachCandidate(key, term, maxCost) { candidate ->
                if (surfacePrefix == null || lookupKey(candidate.surface).startsWith(surfacePrefix)) {
                    if (best.size < limit) {
                        best.add(candidate)
                    } else if (completionOrder.compare(candidate, best.element()) < 0) {
                        best.remove()
                        best.add(candidate)
                    }
                }
            }
        }
        return best.sortedWith(completionOrder)
    }

    private fun forEachCandidate(
        reading: String,
        term: Int,
        maxCost: Int = Int.MAX_VALUE,
        emit: (DictionaryCandidate) -> Unit,
    ) {
        dictionary.tokenArray.forEachDictionaryByYomiTermId(term, dictionary.tokenIndex) { wordCost, nodeId ->
            val cost = wordCost.toInt()
            if (cost > maxCost) return@forEachDictionaryByYomiTermId
            val surface = if (nodeId == -1) reading else dictionary.wordTrie.getLetter(nodeId, dictionary.wordLbsIndex)
            emit(DictionaryCandidate(reading, surface, cost))
        }
    }

    private companion object {
        val completionKey = Regex("[a-z0-9]+")
        val completionOrder = compareBy<DictionaryCandidate> { it.cost }
            .thenBy { it.reading.length }.thenBy { it.reading }.thenBy { it.surface }
    }
}
