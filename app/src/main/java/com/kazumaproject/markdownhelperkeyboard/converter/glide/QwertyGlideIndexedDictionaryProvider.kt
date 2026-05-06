package com.kazumaproject.markdownhelperkeyboard.converter.glide

data class QwertyGlideIndexedEntry(
    val word: String,
    val wordCost: Int,
    val firstChar: Char,
    val lastChar: Char,
    val length: Int,
    val characterMask: Int,
    val transitionMask: Long
) {
    fun asDictionaryEntry(): QwertyGlideDictionaryEntry {
        return QwertyGlideDictionaryEntry(word = word, wordCost = wordCost)
    }
}

class QwertyGlideIndexedDictionaryProvider(
    entries: Iterable<QwertyGlideDictionaryEntry>
) : QwertyGlideDictionaryProvider {
    private val indexedEntries: Map<Pair<Char, Char>, List<QwertyGlideIndexedEntry>>
    val entryCount: Int

    init {
        val deduped = linkedMapOf<String, QwertyGlideDictionaryEntry>()
        entries.asSequence()
            .mapNotNull { entry ->
                val normalized = entry.word.lowercase()
                if (normalized.length < 2 || normalized.any { it !in 'a'..'z' }) {
                    null
                } else {
                    QwertyGlideDictionaryEntry(
                        word = normalized,
                        wordCost = entry.wordCost.coerceAtLeast(0)
                    )
                }
            }
            .forEach { entry ->
                val existing = deduped[entry.word]
                if (existing == null || entry.wordCost < existing.wordCost) {
                    deduped[entry.word] = entry
                }
            }

        val indexed = deduped.values
            .map { it.toIndexedEntry() }
            .sortedWith(compareBy<QwertyGlideIndexedEntry> { it.firstChar }
                .thenBy { it.lastChar }
                .thenBy { it.length }
                .thenBy { it.word }
                .thenBy { it.wordCost })

        entryCount = indexed.size
        indexedEntries = indexed.groupBy { it.firstChar to it.lastChar }
    }

    override fun entriesFor(
        firstChar: Char,
        lastChar: Char,
        minLength: Int,
        maxLength: Int
    ): Sequence<QwertyGlideDictionaryEntry> {
        return indexedEntries[firstChar.lowercaseChar() to lastChar.lowercaseChar()]
            .orEmpty()
            .asSequence()
            .filter { it.length in minLength..maxLength }
            .map { it.asDictionaryEntry() }
    }

    fun indexedEntriesFor(
        firstChars: Collection<Char>,
        lastChars: Collection<Char>,
        minLength: Int,
        maxLength: Int
    ): List<QwertyGlideIndexedEntry> {
        if (firstChars.isEmpty() || lastChars.isEmpty() || minLength > maxLength) return emptyList()
        val result = ArrayList<QwertyGlideIndexedEntry>()
        val seen = HashSet<String>()
        for (first in firstChars.map { it.lowercaseChar() }.distinct()) {
            for (last in lastChars.map { it.lowercaseChar() }.distinct()) {
                indexedEntries[first to last].orEmpty()
                    .asSequence()
                    .filter { it.length in minLength..maxLength }
                    .forEach { entry ->
                        if (seen.add(entry.word)) result.add(entry)
                    }
            }
        }
        result.sortWith(compareBy<QwertyGlideIndexedEntry> { it.word }.thenBy { it.wordCost })
        return result
    }

    fun candidateCountFor(
        firstChars: Collection<Char>,
        lastChars: Collection<Char>,
        minLength: Int,
        maxLength: Int
    ): Int {
        return indexedEntriesFor(firstChars, lastChars, minLength, maxLength).size
    }
}

private fun QwertyGlideDictionaryEntry.toIndexedEntry(): QwertyGlideIndexedEntry {
    return QwertyGlideIndexedEntry(
        word = word,
        wordCost = wordCost,
        firstChar = word.first(),
        lastChar = word.last(),
        length = word.length,
        characterMask = word.characterMask(),
        transitionMask = word.transitionMask()
    )
}

internal fun String.characterMask(): Int {
    var mask = 0
    for (ch in this) {
        if (ch in 'a'..'z') mask = mask or (1 shl (ch - 'a'))
    }
    return mask
}

private fun String.transitionMask(): Long {
    var mask = 0L
    for (i in 1 until length) {
        val from = this[i - 1] - 'a'
        val to = this[i] - 'a'
        if (from in 0..25 && to in 0..25) {
            val bucket = ((from * 31 + to) and 63)
            mask = mask or (1L shl bucket)
        }
    }
    return mask
}
