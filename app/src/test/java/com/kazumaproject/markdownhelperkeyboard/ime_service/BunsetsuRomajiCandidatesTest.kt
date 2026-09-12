package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiConversionSession
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiQueryResult
import com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana.RomajiKanaConverter
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BunsetsuRomajiCandidatesTest {
    private val service = IMEService()
    private val session = mock<KanaKanjiConversionSession>()
    private val school = Candidate(string = "学校", type = 1, length = 4u, score = 1000)
    private val romaji = listOf("ｇａｋｋｏｕ", "gakkou", "Ｇａｋｋｏｕ", "Gakkou", "ＧＡＫＫＯＵ", "GAKKOU")

    @Before fun setUp() = runBlocking {
        service.appPreference = mock()
        service.userDictionaryRepository = mock()
        ReflectionHelpers.getField<CompletableDeferred<KanaKanjiEngine>>(service, "kanaKanjiEngineReady")
            .complete(mock())
        ReflectionHelpers.setField(service, "kanaKanjiConversionSession", session)
        ReflectionHelpers.setField(service, "isLearnDictionaryMode", false)
        ReflectionHelpers.setField(service, "romajiConverter", RomajiKanaConverter(mapOf(
            "ga" to ("が" to 0), "ko" to ("こ" to 0), "u" to ("う" to 0),
        )))
        whenever(session.query(any())).thenReturn(KanaKanjiQueryResult(listOf(school)))
        Unit
    }

    @After fun tearDown() {
        ReflectionHelpers.getField<ExecutorCoroutineDispatcher>(service, "kanaKanjiConversionDispatcher").close()
    }

    @Test fun enabledAddsAllRomajiVariantsWithoutReplacingConvertedDisplay() = runBlocking {
        ReflectionHelpers.setField(service, "conversionCandidatesRomajiEnablePreference", true)
        val result = query()
        assertEquals(listOf("学校") + romaji, result.candidates.map { it.string })
        assertTrue(result.candidates.all { it.length.toInt() == 4 })
        val loaded = mergeBunsetsuCandidates(
            BunsetsuSegmentState("がっこう", "学校", hasConvertedDisplay = true), result.candidates,
        )
        assertEquals("学校", loaded.displayText)
        assertEquals(0, loaded.selectedIndex)
        assertTrue(loaded.candidates.map { it.string }.containsAll(romaji))
    }

    @Test fun disabledDoesNotAddRomaji() = runBlocking {
        ReflectionHelpers.setField(service, "conversionCandidatesRomajiEnablePreference", false)
        assertEquals(listOf(school), query().candidates)
    }

    @Test fun romajiPassesThroughNgWordFilteringAndDeduplication() = runBlocking {
        ReflectionHelpers.setField(service, "conversionCandidatesRomajiEnablePreference", true)
        ReflectionHelpers.setField(service, "isNgWordEnable", true)
        ReflectionHelpers.getField<MutableStateFlow<List<NgWord>>>(service, "_ngWordsList").value =
            listOf(NgWord(yomi = "がっこう", tango = "GAKKOU"))
        val duplicate = school.copy(string = "gakkou")
        whenever(session.query(any())).thenReturn(KanaKanjiQueryResult(listOf(school, duplicate)))
        val candidates = query().candidates
        assertFalse(candidates.any { it.string == "GAKKOU" })
        assertEquals(1, candidates.count { it.string == "gakkou" })
        assertEquals(duplicate, candidates.single { it.string == "gakkou" })
        assertTrue(candidates.any { it.string == "ｇａｋｋｏｕ" })
    }

    @Test fun missingConverterKeepsEngineCandidates() = runBlocking {
        ReflectionHelpers.setField(service, "conversionCandidatesRomajiEnablePreference", true)
        ReflectionHelpers.setField(service, "romajiConverter", null)
        assertEquals(listOf(school), query().candidates)
    }

    private suspend fun query(): KanaKanjiQueryResult = suspendCoroutineUninterceptedOrReturn { continuation ->
        IMEService::class.java.getDeclaredMethod(
            "queryBunsetsuConversion", String::class.java, Continuation::class.java,
        ).apply { isAccessible = true }.invoke(service, "がっこう", continuation)
    }
}
