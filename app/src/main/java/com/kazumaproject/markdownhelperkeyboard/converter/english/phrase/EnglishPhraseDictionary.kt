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
            if (key.length > prefix.length && key.none(Char::isWhitespace)) return true
        }
        return false
    }

    fun hasPhraseCompletion(input: String): Boolean {
        if (!phraseKey.matches(input)) return false
        for (prefix in dictionary.readingTrie.prefixesMatchingJoinedInput(input, dictionary.readingLbsIndex)) {
            val terms = dictionary.readingTrie.termsStartingWith(prefix, dictionary.readingLbsIndex, dictionary.readingLeafIndex)
            for ((reading, term) in terms) {
                if (' ' !in reading || !phraseKey.matches(reading)) continue
                var found = false
                forEachCandidate(reading, term) { if (' ' in it.surface) found = true }
                if (found) return true
            }
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
        val terms = dictionary.readingTrie.termsStartingWith(
            prefix, dictionary.readingLbsIndex, dictionary.readingLeafIndex,
            dictionary.wordCompletionBounds?.let { bounds ->
                { label ->
                    val bound = bounds[label]
                    bound != Int.MAX_VALUE && (best.size < limit || bound <= completionRank(best.element()))
                }
            },
        )
        for ((key, term) in terms) {
            if (key == prefix || key.any { it !in 'a'..'z' && it !in '0'..'9' }) continue
            val maxCost = if (best.size == limit) best.element().cost else Int.MAX_VALUE
            forEachCandidate(key, term, maxCost) { candidate ->
                if (candidate.surface.none(Char::isWhitespace) &&
                    (surfacePrefix == null || lookupKey(candidate.surface).startsWith(surfacePrefix))
                ) {
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
        val normalized = lookupKey(input).replace(whitespace, " ").trimStart()
        val prefix = if (prefixMode) normalized else normalized.trimEnd()
        if (prefix.length < 2 || !phraseKey.matches(prefix.trimEnd())) return emptyList()
        val compact = prefix.replace(" ", "")
        val order = compareBy<DictionaryCandidate> { if (it.reading.replace(" ", "") == compact) 0 else 1 }
            .then(completionOrder)
        val best = PriorityQueue(order.reversed())

        fun collect(readingPrefix: String) {
            val terms = if (prefixMode) dictionary.readingTrie.termsStartingWith(
                readingPrefix, dictionary.readingLbsIndex, dictionary.readingLeafIndex,
                dictionary.phraseCompletionBounds?.let { bounds ->
                    { label ->
                        val bound = bounds[label]
                        if (bound == Int.MAX_VALUE) false
                        else if (best.size < limit) true
                        else {
                            // The prefix node itself is still visited. Descendants have
                            // extra typed characters and cannot be an exact alias.
                            val worst = best.element()
                            bound <= completionRank(worst) && worst.reading.count { it != ' ' } != compact.length
                        }
                    }
                },
            ) else {
                val node = dictionary.readingTrie.getNodeIndex(readingPrefix, dictionary.readingLbsIndex)
                if (node < 0 || !dictionary.readingTrie.isLeaf[node]) return
                listOf(readingPrefix to dictionary.readingTrie.getTermId(node, dictionary.readingLeafIndex)).iterator()
            }
            for ((reading, term) in terms) {
                if (' ' !in reading || !phraseKey.matches(reading)) continue
                val matches = if (' ' in prefix) {
                    if (prefixMode) reading.startsWith(prefix) else reading == prefix
                } else {
                    val joined = reading.replace(" ", "")
                    if (prefixMode) joined.startsWith(compact) else joined == compact
                }
                if (!matches) continue
                val worst = best.peek()
                val exact = reading.count { it != ' ' } == compact.length
                val maxCost = if (!exact && best.size == limit) {
                    val cutoff = checkNotNull(worst)
                    if (cutoff.reading.count { it != ' ' } == compact.length) continue
                    cutoff.cost
                } else Int.MAX_VALUE
                forEachCandidate(reading, term, maxCost) { candidate ->
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

        if (' ' in prefix) collect(prefix) else {
            for (matching in joinedPhrasePrefixes(prefix, prefixMode)) collect(matching)
        }
        return best.sortedWith(order)
    }

    private fun joinedPhrasePrefixes(input: String, prefixMode: Boolean): List<String> {
        val roles = grammarRoles.lookup(input, input)
        // An article or another function word does not imply the start of a name.
        if (roles.any(::isFunctionWordRole)) return emptyList()
        val joined = dictionary.readingTrie.prefixesMatchingJoinedInput(input, dictionary.readingLbsIndex)
            .filter { ' ' in it }
        // Follow typed characters into the next word. A complete content word
        // can also lead to its registered phrases, such as Android Studio.
        return if (prefixMode && roles.any { it.startsWith("noun_") || it.startsWith("name_") } &&
            exact(input).any { lookupKey(it.surface) == input }
        ) {
            joined + "$input "
        } else joined
    }

    private fun completionRank(candidate: DictionaryCandidate): Int =
        (candidate.cost shl 16) or candidate.reading.length.coerceAtMost(65_534)

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
