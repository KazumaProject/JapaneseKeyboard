package com.kazumaproject.markdownhelperkeyboard.converter.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QwertyGlideIndexedDictionaryProviderTest {
    @Test
    fun indexedDictionaryFiltersAndIndexesByFirstLastAndLength() {
        val provider = QwertyGlideIndexedDictionaryProvider(
            listOf(
                QwertyGlideDictionaryEntry("Hello", 10),
                QwertyGlideDictionaryEntry("help", 20),
                QwertyGlideDictionaryEntry("h2o", 1),
                QwertyGlideDictionaryEntry("a", 1),
                QwertyGlideDictionaryEntry("world", 30)
            )
        )

        val entries = provider.indexedEntriesFor(listOf('h'), listOf('o', 'p'), 2, 5)

        assertEquals(listOf("hello", "help"), entries.map { it.word })
        assertEquals(3, provider.entryCount)
        assertTrue(provider.entriesFor('w', 'd', 2, 5).toList().single().word == "world")
    }

    @Test
    fun duplicateWordsKeepLowestCostDeterministically() {
        val provider = QwertyGlideIndexedDictionaryProvider(
            listOf(
                QwertyGlideDictionaryEntry("test", 900),
                QwertyGlideDictionaryEntry("Test", 100),
                QwertyGlideDictionaryEntry("tent", 200)
            )
        )

        val entries = provider.indexedEntriesFor(listOf('t'), listOf('t'), 2, 8)

        assertEquals(listOf("tent", "test"), entries.map { it.word })
        assertEquals(100, entries.single { it.word == "test" }.wordCost)
    }

    @Test
    fun emptyDictionaryAndLengthBoundsAreSafe() {
        val provider = QwertyGlideIndexedDictionaryProvider(emptyList())

        assertTrue(provider.indexedEntriesFor(listOf('a'), listOf('z'), 2, 12).isEmpty())
        assertTrue(provider.entriesFor('a', 'z', 2, 12).toList().isEmpty())
    }

    @Test
    fun indexedEntriesExposeReusableFeatures() {
        val provider = QwertyGlideIndexedDictionaryProvider(
            listOf(QwertyGlideDictionaryEntry("keyboard", 42))
        )

        val entry = provider.indexedEntriesFor(listOf('k'), listOf('d'), 2, 24).single()

        assertEquals('k', entry.firstChar)
        assertEquals('d', entry.lastChar)
        assertEquals(8, entry.length)
        assertTrue(entry.characterMask and (1 shl ('k' - 'a')) != 0)
        assertTrue(entry.transitionMask != 0L)
    }
}
