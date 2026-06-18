package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcCandidateProvider
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcConversionOptions
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class KanaKanjiEngineMozcCompatibleRoutingTest {
    @Test
    fun onPathCallsMozcCompatibleConverterAndReturnsItsCandidates() = runTest {
        val fakeProvider = RecordingMozcProvider()
        val engine = KanaKanjiEngine().apply {
            setMozcCompatibleConverterForTesting(fakeProvider)
        }

        val candidates = engine.getCandidates(
            input = "きょう",
            n = 3,
            mozcUtPersonName = false,
            mozcUTPlaces = false,
            mozcUTWiki = false,
            mozcUTNeologd = false,
            mozcUTWeb = false,
            userDictionaryRepository = mock<UserDictionaryRepository>(),
            learnRepository = null,
            isOmissionSearchEnable = false,
            typoCorrectionOffsetScore = 3000,
            omissionSearchOffsetScore = 1900,
            enableMozcCompatibleConversion = true,
        )

        assertEquals(1, fakeProvider.callCount)
        assertEquals("今日", candidates.single().string)
        assertEquals("きょう", candidates.single().yomi)
        assertEquals(false, fakeProvider.lastOptions?.isOmissionSearchEnabled)
    }

    @Test
    fun onPathPassesOmissionSearchSettingsToMozcCompatibleConverter() = runTest {
        val fakeProvider = RecordingMozcProvider()
        val engine = KanaKanjiEngine().apply {
            setMozcCompatibleConverterForTesting(fakeProvider)
        }

        engine.getCandidates(
            input = "かくせい",
            n = 7,
            mozcUtPersonName = false,
            mozcUTPlaces = false,
            mozcUTWiki = false,
            mozcUTNeologd = false,
            mozcUTWeb = false,
            userDictionaryRepository = mock<UserDictionaryRepository>(),
            learnRepository = null,
            isOmissionSearchEnable = true,
            typoCorrectionOffsetScore = 3000,
            omissionSearchOffsetScore = 2345,
            enableMozcCompatibleConversion = true,
        )

        assertEquals(1, fakeProvider.callCount)
        assertEquals(7, fakeProvider.lastOptions?.nBest)
        assertEquals(true, fakeProvider.lastOptions?.isOmissionSearchEnabled)
        assertEquals(2345, fakeProvider.lastOptions?.omissionSearchOffsetScore)
    }

    @Test
    fun offPathDoesNotCallMozcCompatibleConverter() = runTest {
        val fakeProvider = RecordingMozcProvider()
        val engine = KanaKanjiEngine().apply {
            setMozcCompatibleConverterForTesting(fakeProvider)
        }

        listOf("きょう", "わたし", "にほんご", "へんかん").forEach { input ->
            runCatching {
                engine.getCandidates(
                    input = input,
                    n = 3,
                    mozcUtPersonName = false,
                    mozcUTPlaces = false,
                    mozcUTWiki = false,
                    mozcUTNeologd = false,
                    mozcUTWeb = false,
                    userDictionaryRepository = mock<UserDictionaryRepository>(),
                    learnRepository = null,
                    isOmissionSearchEnable = false,
                    typoCorrectionOffsetScore = 3000,
                    omissionSearchOffsetScore = 1900,
                    enableMozcCompatibleConversion = false,
                )
            }
        }

        assertEquals(0, fakeProvider.callCount)
    }

    @Test
    fun onPathDoesNotFallBackToLegacyWhenMozcReturnsEmptyCandidates() = runTest {
        val fakeProvider = EmptyMozcProvider()
        val engine = KanaKanjiEngine().apply {
            setMozcCompatibleConverterForTesting(fakeProvider)
        }

        val result = runCatching {
            engine.getCandidates(
                input = "きょう",
                n = 3,
                mozcUtPersonName = false,
                mozcUTPlaces = false,
                mozcUTWiki = false,
                mozcUTNeologd = false,
                mozcUTWeb = false,
                userDictionaryRepository = mock<UserDictionaryRepository>(),
                learnRepository = null,
                isOmissionSearchEnable = false,
                typoCorrectionOffsetScore = 3000,
                omissionSearchOffsetScore = 1900,
                enableMozcCompatibleConversion = true,
            )
        }

        assertEquals(1, fakeProvider.callCount)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("returned empty candidates") == true)
    }

    private class RecordingMozcProvider : MozcCandidateProvider {
        var callCount: Int = 0
        var lastOptions: MozcConversionOptions? = null

        override fun getCandidates(
            input: String,
            options: MozcConversionOptions,
        ): List<Candidate> {
            callCount++
            lastOptions = options
            assertTrue(options.nBest > 0)
            return listOf(
                Candidate(
                    string = "今日",
                    type = 1,
                    length = input.length.toUByte(),
                    score = 10,
                    yomi = input,
                    leftId = 1,
                    rightId = 1,
                ),
            )
        }
    }

    private class EmptyMozcProvider : MozcCandidateProvider {
        var callCount: Int = 0

        override fun getCandidates(
            input: String,
            options: MozcConversionOptions,
        ): List<Candidate> {
            callCount++
            return emptyList()
        }
    }
}
