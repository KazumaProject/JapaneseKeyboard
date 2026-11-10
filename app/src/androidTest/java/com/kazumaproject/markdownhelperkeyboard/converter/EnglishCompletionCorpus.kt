package com.kazumaproject.markdownhelperkeyboard.converter

import com.kazumaproject.markdownhelperkeyboard.converter.compact.CompactEnglishDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.english.phrase.EnglishPhraseDictionary

/** Reference words are selected from dictionary data, independently of the engine's output. */
internal class EnglishCompletionCorpus(
    data: CompactEnglishDictionary,
    dictionary: EnglishPhraseDictionary,
) {
    private data class Word(val text: String, val cost: Int)

    private val rankedWords = buildList {
        val terms = data.readingTrie.termsStartingWith("", data.readingLbsIndex, data.readingLeafIndex)
        for ((reading, term) in terms) {
            if (reading.length !in 2..12 || reading.any { it !in 'a'..'z' }) continue
            var cost = Int.MAX_VALUE
            data.tokenArray.forEachDictionaryByYomiTermId(term, data.tokenIndex) { wordCost, node ->
                if (node == -1) cost = minOf(cost, wordCost.toInt())
            }
            if (cost !in 0..5200) continue
            val roles = dictionary.grammarRoles.lookup(reading, reading)
            if (roles.any { it in ordinaryRoles || it.startsWith("verb_") || it.startsWith("noun_count_") }) {
                add(Word(reading, cost))
            }
        }
    }.sortedWith(compareBy<Word> { it.cost }.thenBy { it.text.length }.thenBy { it.text })

    val ordinary = (controls + rankedWords.map(Word::text)).distinct().sorted()
    val firstKeys = ('a'..'z').map(Char::toString)
    val expectedCompletions = buildMap<String, MutableList<String>> {
        for (entry in rankedWords) {
            val word = entry.text
            for (length in 2 until word.length) {
                val expected = getOrPut(word.take(length), ::mutableListOf)
                if (expected.size < 3) expected += word
            }
        }
    }

    // Include sparse or absent bigrams too. A scan of dictionary words alone skips them.
    val bigrams = ('a'..'z').flatMap { first -> ('a'..'z').map { second -> "$first$second" } }
    val partial = (firstKeys + bigrams + ordinary.flatMap { word ->
        (3 until word.length).map(word::take)
    }).distinct().sorted()

    private companion object {
        val controls = ("a i the to is in on he she we it at an be can do go my no of so up us by " +
            "am as or if and but not for has had how her his are was you who one two get got new " +
            "now day say see all any use way yes why read write good right home make made take took " +
            "come came done went will work play look time like love want find keep give help water " +
            "friend family school world people think thank please going thing really").split(' ')

        val ordinaryRoles = setOf(
            "pronoun", "determiner", "conjunction", "preposition",
            "noun_uncountable", "adjective", "adverb", "interjection", "contraction",
        )
    }
}
