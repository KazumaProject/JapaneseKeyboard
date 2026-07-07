package com.kazumaproject.markdownhelperkeyboard.converter.glide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QwertyGlideWarmupLifecycleCacheTest {
    @Test
    fun fallbackDecoderBeforeWarmupDoesNotCrashAndReadyDecoderReportsReady() {
        val fallbackMetrics = mutableListOf<QwertyGlideDecodeMetrics>()
        val fallbackDecoder = QwertyGlideDecoder(
            dictionaryProvider = QwertyGlideIndexedDictionaryProvider(QwertyGlideTestFixtures.dictionary()),
            dictionaryReady = false,
            metricsListener = fallbackMetrics::add
        )

        val fallbackCandidates = fallbackDecoder.decode(
            inputPointers = QwertyGlideTestFixtures.strokeFor("hello"),
            proximityInfo = QwertyGlideTestFixtures.proximityInfo,
            previousText = "",
            limit = 6
        )

        assertTrue(fallbackCandidates.isNotEmpty())
        assertFalse(fallbackMetrics.last().dictionaryReady)

        val readyMetrics = mutableListOf<QwertyGlideDecodeMetrics>()
        val readyDecoder = QwertyGlideDecoder(
            dictionaryProvider = QwertyGlideIndexedDictionaryProvider(
                QwertyGlideTestFixtures.dictionary(extraNoiseCount = 1000)
            ),
            dictionaryReady = true,
            metricsListener = readyMetrics::add
        )
        readyDecoder.decode(QwertyGlideTestFixtures.strokeFor("hello"), QwertyGlideTestFixtures.proximityInfo, "", 6)

        assertTrue(readyMetrics.last().dictionaryReady)
        assertTrue(readyMetrics.last().rawBucketCandidateCount >= fallbackMetrics.last().rawBucketCandidateCount)
    }

    @Test
    fun cacheHitsSameStrokeAndInvalidatesOnClearOrGeometryChange() {
        val metrics = mutableListOf<QwertyGlideDecodeMetrics>()
        val decoder = QwertyGlideDecoder(
            dictionaryProvider = QwertyGlideIndexedDictionaryProvider(QwertyGlideTestFixtures.dictionary()),
            dictionaryReady = true,
            metricsListener = metrics::add
        )
        val stroke = QwertyGlideTestFixtures.strokeFor("world")

        decoder.decode(stroke, QwertyGlideTestFixtures.proximityInfo, "", 6)
        decoder.decode(stroke, QwertyGlideTestFixtures.proximityInfo, "", 12)
        assertTrue(metrics.last().cacheHit)

        decoder.clearCache()
        decoder.decode(stroke, QwertyGlideTestFixtures.proximityInfo, "", 6)
        assertFalse(metrics.last().cacheHit)

        val changedGeometry = QwertyGlideTestFixtures.proximityInfo.copy(keyboardWidth = 1010)
        decoder.decode(stroke, changedGeometry, "", 6)
        assertFalse(metrics.last().cacheHit)
        assertNotEquals(QwertyGlideTestFixtures.proximityInfo.geometrySignature(), changedGeometry.geometrySignature())
    }

    @Test
    fun multipleDecoderWarmupsAndCancellationRecoveryAreRepresentedByFreshReadyDecoder() {
        val cancelledLikeDecoder = QwertyGlideDecoder(
            dictionaryProvider = QwertyGlideIndexedDictionaryProvider(QwertyGlideTestFixtures.dictionary()),
            dictionaryReady = false
        )
        val readyDecoder = QwertyGlideDecoder(
            dictionaryProvider = QwertyGlideIndexedDictionaryProvider(QwertyGlideTestFixtures.dictionary(extraNoiseCount = 500)),
            dictionaryReady = true
        )

        assertTrue(
            cancelledLikeDecoder.decode(
                QwertyGlideTestFixtures.strokeFor("test"),
                QwertyGlideTestFixtures.proximityInfo,
                "",
                6
            ).isNotEmpty()
        )
        assertTrue(
            readyDecoder.decode(
                QwertyGlideTestFixtures.strokeFor("test"),
                QwertyGlideTestFixtures.proximityInfo,
                "",
                6
            ).isNotEmpty()
        )
    }
}
