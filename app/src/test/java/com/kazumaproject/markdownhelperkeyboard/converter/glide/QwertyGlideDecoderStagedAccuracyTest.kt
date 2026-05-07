package com.kazumaproject.markdownhelperkeyboard.converter.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QwertyGlideDecoderStagedAccuracyTest {
    @Test
    fun expectedWordsRemainInTopSixForGoldenStrokes() {
        val decoder = decoder(QwertyGlideTestFixtures.dictionary(extraNoiseCount = 5000))
        val words = listOf("hello", "world", "test", "keyboard", "glide", "good", "time", "home", "something")

        for (word in words) {
            val candidates = decoder.decode(
                inputPointers = QwertyGlideTestFixtures.strokeFor(word),
                proximityInfo = QwertyGlideTestFixtures.proximityInfo,
                previousText = "",
                limit = 6
            ).map { it.string }

            assertTrue("$word should remain in top6, actual=$candidates", word in candidates)
        }
    }

    @Test
    fun startAndEndAmbiguityDoesNotDropExpectedCandidate() {
        val decoder = decoder(QwertyGlideTestFixtures.dictionary(extraNoiseCount = 2000))

        val candidates = decoder.decode(
            inputPointers = QwertyGlideTestFixtures.strokeFor(
                word = "hello",
                startOffsetX = 24f,
                startOffsetY = -18f,
                endOffsetX = -22f,
                endOffsetY = 18f
            ),
            proximityInfo = QwertyGlideTestFixtures.proximityInfo,
            previousText = "",
            limit = 6
        ).map { it.string }

        assertTrue("hello should survive ambiguous start/end, actual=$candidates", "hello" in candidates)
    }

    @Test
    fun stagedPrefilterReducesFullScorerInputAndKeepsCandidate() {
        val metrics = mutableListOf<QwertyGlideDecodeMetrics>()
        val entries = QwertyGlideTestFixtures.dictionary() +
                QwertyGlideTestFixtures.sameBucketNoise(5000, first = 'h', last = 'o')
        val decoder = decoder(entries, metrics::add)

        val candidates = decoder.decode(
            inputPointers = QwertyGlideTestFixtures.strokeFor("hello"),
            proximityInfo = QwertyGlideTestFixtures.proximityInfo,
            previousText = "",
            limit = 6
        ).map { it.string }
        val lastMetrics = metrics.last()

        assertTrue("hello should remain after cheap prefilter, actual=$candidates", "hello" in candidates)
        assertTrue(lastMetrics.rawBucketCandidateCount > lastMetrics.fullScoreCandidateCount)
        assertTrue(lastMetrics.fullScoreCandidateCount <= QwertyGlideDecodeOptions().fullScoreCandidateLimit)
    }

    @Test
    fun decodeIsDeterministicForSameInput() {
        val decoder = decoder(QwertyGlideTestFixtures.dictionary(extraNoiseCount = 1000))
        val stroke = QwertyGlideTestFixtures.strokeFor("world")

        val first = decoder.decode(stroke, QwertyGlideTestFixtures.proximityInfo, "", 12)
        decoder.clearCache()
        val second = decoder.decode(stroke, QwertyGlideTestFixtures.proximityInfo, "", 12)

        assertEquals(first.map { it.string }, second.map { it.string })
    }

    private fun decoder(
        entries: List<QwertyGlideDictionaryEntry>,
        metricsListener: ((QwertyGlideDecodeMetrics) -> Unit)? = null
    ): QwertyGlideDecoder {
        return QwertyGlideDecoder(
            dictionaryProvider = QwertyGlideIndexedDictionaryProvider(entries),
            options = QwertyGlideDecodeOptions(),
            dictionaryReady = true,
            metricsListener = metricsListener
        )
    }
}
