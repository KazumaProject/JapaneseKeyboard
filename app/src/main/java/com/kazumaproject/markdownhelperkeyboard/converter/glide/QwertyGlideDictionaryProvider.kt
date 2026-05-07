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
    private val indexedProvider = QwertyGlideIndexedDictionaryProvider(entries)

    override fun entriesFor(
        firstChar: Char,
        lastChar: Char,
        minLength: Int,
        maxLength: Int
    ): Sequence<QwertyGlideDictionaryEntry> {
        return indexedProvider.entriesFor(firstChar, lastChar, minLength, maxLength)
    }
}
