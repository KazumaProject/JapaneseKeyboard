package com.kazumaproject.markdownhelperkeyboard.converter.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QwertyGlideDecoderAccuracyTest {
    private val words = listOf(
        "hello",
        "good",
        "test",
        "word",
        "keyboard",
        "android",
        "sumire",
        "coffee",
        "letter",
        "people",
        "help",
        "held",
        "gold",
        "toast",
        "work",
        "key",
        "and",
        "to",
        "of",
        "in",
        "on",
        "he",
        "go"
    )
    private val proximityInfo = FixedQwertyGeometryFactory.create()
    private val strokeFactory = SyntheticQwertyGlideStrokeFactory(proximityInfo)
    private val decoder = QwertyGlideDecoder(
        dictionaryProvider = InMemoryQwertyGlideDictionaryProvider(
            words.mapIndexed { index, word ->
                QwertyGlideDictionaryEntry(word, 5000 + index * 20)
            }
        ),
        options = QwertyGlideDecodeOptions(
            maxResults = 12,
            beamWidth = 128,
            pointKeyTopK = 6
        )
    )

    @Test
    fun idealStrokesPutExpectedWordsTop1() {
        targetWords.forEach { word ->
            val candidates = decoder.decode(strokeFactory.ideal(word), proximityInfo, previousText = "")
            assertEquals("ideal $word candidates=${candidates.map { it.string to it.score }}", word, candidates.firstOrNull()?.string)
        }
    }

    @Test
    fun noisyStrokesPutExpectedWordsTop3() {
        targetWords.forEach { word ->
            val candidates = decoder.decode(strokeFactory.noisy(word), proximityInfo, previousText = "")
            assertTrue("noisy $word candidates=${candidates.map { it.string }}", candidates.take(3).any { it.string == word })
        }
    }

    @Test
    fun fastSparseStrokesPutExpectedWordsTop3() {
        targetWords.forEach { word ->
            val candidates = decoder.decode(strokeFactory.fastSparse(word), proximityInfo, previousText = "")
            assertTrue("fastSparse $word candidates=${candidates.map { it.string }}", candidates.take(3).any { it.string == word })
        }
    }

    @Test
    fun repeatedLettersAreNotCollapsedAway() {
        listOf("hello", "good", "coffee", "letter", "people").forEach { word ->
            val candidates = decoder.decode(strokeFactory.repeatedLetter(word), proximityInfo, previousText = "")
            assertTrue("repeated $word candidates=${candidates.map { it.string }}", candidates.take(3).any { it.string == word })
        }
    }

    @Test
    fun differentStartOrEndCompetitorDoesNotWin() {
        val candidates = decoder.decode(strokeFactory.ideal("word"), proximityInfo, previousText = "")
        assertNotEquals("work", candidates.firstOrNull()?.string)
        assertEquals("word", candidates.firstOrNull()?.string)
    }

    @Test
    fun shortHighFrequencyWordsDoNotWinLongStroke() {
        val candidates = decoder.decode(strokeFactory.ideal("keyboard"), proximityInfo, previousText = "")
        assertEquals("keyboard", candidates.firstOrNull()?.string)
        assertTrue(candidates.take(3).none { it.string in setOf("key", "to", "of", "in") })
    }

    companion object {
        private val targetWords = listOf(
            "hello",
            "good",
            "test",
            "word",
            "keyboard",
            "android",
            "sumire",
            "coffee",
            "letter",
            "people"
        )
    }
}
