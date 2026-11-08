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

    fun hasCompletion(prefix: String): Boolean {
        val terms = dictionary.readingTrie.termsStartingWith(prefix, dictionary.readingLbsIndex, dictionary.readingLeafIndex)
        for ((key, _) in terms) {
            if (key.length > prefix.length && key.all { it in 'a'..'z' || it in '0'..'9' }) return true
        }
        return false
    }

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
            if (key == prefix || key.any { it !in 'a'..'z' && it !in '0'..'9' }) continue
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

    /** Finds only dictionary-backed multiword surfaces, including joined-input aliases. */
    fun phrases(input: String, prefixMode: Boolean, limit: Int): List<DictionaryCandidate> {
        val prefix = lookupKey(input).trim().replace(whitespace, " ")
        if (prefix.length < 2 || !phraseKey.matches(prefix)) return emptyList()
        val compact = prefix.replace(" ", "")
        val order = compareBy<DictionaryCandidate> { if (it.reading.replace(" ", "") == compact) 0 else 1 }
            .then(completionOrder)
        val best = PriorityQueue(order.reversed())

        fun collect(readingPrefix: String) {
            val terms = dictionary.readingTrie.termsStartingWith(
                readingPrefix, dictionary.readingLbsIndex, dictionary.readingLeafIndex,
            )
            for ((reading, term) in terms) {
                if (' ' !in reading || !phraseKey.matches(reading)) continue
                val matches = if (' ' in prefix) {
                    if (prefixMode) reading.startsWith(prefix) else reading == prefix
                } else {
                    val joined = reading.replace(" ", "")
                    if (prefixMode) joined.startsWith(compact) else joined == compact
                }
                if (!matches) continue
                forEachCandidate(reading, term) { candidate ->
                    if (' ' in candidate.surface) {
                        if (best.size < limit) best.add(candidate)
                        else if (order.compare(candidate, best.element()) < 0) {
                            best.remove()
                            best.add(candidate)
                        }
                    }
                }
            }
        }

        collect(prefix)
        if (' ' !in prefix) {
            // Each subtree has a different first space. This avoids a second full
            // dictionary index and does not guess word boundaries in the surface.
            for (end in 1 until prefix.length) collect(prefix.take(end) + " ")
        }
        return best.sortedWith(order)
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
        val whitespace = Regex("\\s+")
        val phraseKey = Regex("[a-z0-9]+(?: [a-z0-9]+)*")
        val completionOrder = compareBy<DictionaryCandidate> { it.cost }
            .thenBy { it.reading.length }.thenBy { it.reading }.thenBy { it.surface }
    }
}
