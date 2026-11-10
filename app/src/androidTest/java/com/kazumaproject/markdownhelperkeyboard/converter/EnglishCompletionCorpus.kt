package com.kazumaproject.markdownhelperkeyboard.converter

import com.kazumaproject.markdownhelperkeyboard.converter.compact.CompactEnglishDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.english.phrase.EnglishPhraseDictionary
import java.util.PriorityQueue

/** Deterministically samples ordinary words from the same vocabulary the IME loads. */
internal object EnglishCompletionCorpus {
    private data class Sample(val text: String, val priority: Int)

    fun words(data: CompactEnglishDictionary, words: EnglishPhraseDictionary): List<String> {
        val order = compareBy<Sample> { it.priority }.thenBy { it.text }
        val buckets = sortedMapOf<Int, PriorityQueue<Sample>>()
        val terms = data.readingTrie.termsStartingWith("", data.readingLbsIndex, data.readingLeafIndex)
        for ((reading, term) in terms) {
            if (reading.length !in 2..12 || reading.any { it !in 'a'..'z' }) continue
            var cost = Int.MAX_VALUE
            data.tokenArray.forEachDictionaryByYomiTermId(term, data.tokenIndex) { wordCost, node ->
                if (node == -1) cost = minOf(cost, wordCost.toInt())
            }
            if (cost !in 0..5200) continue
            val roles = words.grammarRoles.lookup(reading, reading)
            if (roles.none { it in ordinaryRoles || it.startsWith("verb_") || it.startsWith("noun_count_") }) continue
            val bucket = reading.length.coerceAtMost(7)
            val queue = buckets.getOrPut(bucket) { PriorityQueue(order.reversed()) }
            queue.add(Sample(reading, Integer.rotateLeft(reading.hashCode() * -1640531527, 13)))
            if (queue.size > 32) queue.remove()
        }
        check(buckets.size == 6 && buckets.values.all { it.size == 32 }) { "Insufficient coverage by word length" }
        val sampled = buckets.values.flatMap { queue -> queue.sortedWith(order).map { it.text } }
        return (controls + sampled).distinct().sorted()
    }

    private val controls = ("a i the to is in on he she we it at an be can do go my no of so up us by " +
        "am as or if and but not for has had how her his are was you who one two get got new " +
        "now day say see all any use way yes why read write good right home make made take took " +
        "come came done went will work play look time like love want find keep give help water " +
        "friend family school world people think thank please going thing really").split(' ')

    private val ordinaryRoles = setOf("pronoun", "determiner", "conjunction", "preposition",
        "noun_uncountable", "adjective", "adverb", "interjection", "contraction")
}
