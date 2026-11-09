package com.kazumaproject.markdownhelperkeyboard.converter

import com.kazumaproject.markdownhelperkeyboard.converter.compact.CompactEnglishDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.english.phrase.EnglishPhraseDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.english.phrase.lookupKey
import java.util.PriorityQueue

/** Samples the installed dictionary, rather than a list of particular people or products. */
internal object EnglishBoundaryCorpus {
    data class Entry(val bucket: String, val reading: String, val surface: String, val priority: Long)

    fun sample(
        data: CompactEnglishDictionary,
        words: EnglishPhraseDictionary,
        seed: Long = 0x341d52afL,
        perBucket: Int = 8,
    ): List<Entry> {
        val buckets = sortedMapOf<String, PriorityQueue<Entry>>()
        val terms = data.readingTrie.termsStartingWith("", data.readingLbsIndex, data.readingLeafIndex)
        for ((key, _) in terms) {
            if (key.length !in 4..24 || key.any { it !in 'a'..'z' && it !in '0'..'9' && it !in " '-" }) continue
            var hash = key.hashCode().toLong() xor seed
            hash = (hash xor (hash ushr 16)) * 0x45d9f3bL
            hash = (hash xor (hash ushr 16)) and 0x7fffffffL
            if (hash % 32 != 0L) continue
            val candidate = words.exact(key).minByOrNull { it.cost } ?: continue
            val roles = words.grammarRoles.lookup(candidate.surface, lookupKey(candidate.surface))
            val kind = when {
                ' ' in key -> "compound"
                key.any { it in "'-" } -> "punctuation"
                key.any(Char::isDigit) -> "alphanumeric"
                roles.any { it == "name_person" || it == "noun_proper" } -> "proper"
                else -> "other_word"
            }
            val bucket = kind + if (key.length <= 8) "/short" else "/long"
            val entries = buckets.getOrPut(bucket) { PriorityQueue(compareByDescending<Entry> { it.priority }) }
            entries.add(Entry(bucket, key, candidate.surface, hash))
            if (entries.size > perBucket) entries.remove()
        }
        check(buckets.size == 10 && buckets.values.all { it.size == perBucket }) {
            "Incomplete dictionary coverage: ${buckets.mapValues { it.value.size }}"
        }
        return buckets.values.flatMap { it.sortedBy(Entry::priority) }
    }
}
