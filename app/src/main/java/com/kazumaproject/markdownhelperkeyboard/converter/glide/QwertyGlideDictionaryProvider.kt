package com.kazumaproject.markdownhelperkeyboard.converter.glide

data class QwertyGlideDictionaryEntry(
    val word: String,
    val wordCost: Int
)

interface QwertyGlideDictionaryProvider {
    fun entriesFor(
        firstChar: Char,
        lastChar: Char,
        minLength: Int,
        maxLength: Int
    ): Sequence<QwertyGlideDictionaryEntry>
}

class InMemoryQwertyGlideDictionaryProvider(
    entries: Iterable<QwertyGlideDictionaryEntry>
) : QwertyGlideDictionaryProvider {
    private val indexedEntries: Map<Pair<Char, Char>, List<QwertyGlideDictionaryEntry>> =
        entries
            .asSequence()
            .filter { it.word.length >= 2 }
            .map { it.copy(word = it.word.lowercase()) }
            .filter { it.word.all { ch -> ch in 'a'..'z' } }
            .distinctBy { it.word }
            .groupBy { it.word.first() to it.word.last() }

    override fun entriesFor(
        firstChar: Char,
        lastChar: Char,
        minLength: Int,
        maxLength: Int
    ): Sequence<QwertyGlideDictionaryEntry> {
        return indexedEntries[firstChar to lastChar]
            .orEmpty()
            .asSequence()
            .filter { it.word.length in minLength..maxLength }
    }
}
