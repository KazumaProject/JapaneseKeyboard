package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_TEMPLATE
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiConversionSession
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiQueryResult
import com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana.RomajiKanaConverter
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWord
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWordMatchMode
import com.kazumaproject.markdownhelperkeyboard.user_template.database.UserTemplate
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
import org.mockito.kotlin.verifyNoInteractions
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
        service.userTemplateRepository = mock()
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

    @Test fun templatesRemainSelectableWithoutReplacingSentenceDisplay() = runBlocking {
        ReflectionHelpers.setField(service, "isUserTemplateEnable", true)
        whenever(service.userTemplateRepository.searchByReading("がっこう", 8)).thenReturn(listOf(
            template("学校への連絡です", 200), template("学校を欠席します", 100),
        ))
        val candidates = query().candidates
        assertEquals(listOf("学校を欠席します", "学校への連絡です", "学校"), candidates.map { it.string })
        assertTrue(candidates.take(2).all { it.type == CANDIDATE_TYPE_USER_TEMPLATE })
        val loaded = mergeBunsetsuCandidates(
            BunsetsuSegmentState("がっこう", "学校", hasConvertedDisplay = true), candidates,
        )
        assertEquals(listOf("学校", "学校を欠席します", "学校への連絡です"), loaded.candidates.map { it.string })
        assertEquals("学校", loaded.displayText)
    }

    @Test fun disabledTemplatesAreNotQueried() = runBlocking {
        ReflectionHelpers.setField(service, "isUserTemplateEnable", false)
        assertEquals(listOf(school), query().candidates)
        verifyNoInteractions(service.userTemplateRepository)
    }

    @Test fun templatesAreFilteredAndDeduplicatedWithEngineCandidates() = runBlocking {
        ReflectionHelpers.setField(service, "isUserTemplateEnable", true)
        ReflectionHelpers.setField(service, "isNgWordEnable", true)
        whenever(service.userTemplateRepository.searchByReading("がっこう", 8)).thenReturn(listOf(
            template("学校を欠席します", 100), template("学校", 200),
        ))
        ReflectionHelpers.getField<MutableStateFlow<List<NgWord>>>(service, "_ngWordsList").value =
            listOf(NgWord(yomi = "がっこう", tango = "学校を欠席します", matchMode = NgWordMatchMode.EXACT))
        val candidates = query().candidates
        assertEquals(listOf("学校"), candidates.map { it.string })
        assertEquals(CANDIDATE_TYPE_USER_TEMPLATE, candidates.single().type)
    }

    @Test fun exactNgWordInvalidatesUnfocusedProjectedDisplayBeforeCandidateLoading() {
        ReflectionHelpers.setField(service, "isNgWordEnable", true)
        ReflectionHelpers.getField<MutableStateFlow<List<NgWord>>>(service, "_ngWordsList").value =
            listOf(NgWord(yomi = "いく", tango = "行く", matchMode = NgWordMatchMode.EXACT))
        val segments = projectSchoolSentence()
        assertEquals("学校に", segments[0].displayText)
        assertTrue(segments[0].hasConvertedDisplay)
        assertEquals("いく", segments[1].displayText)
        assertFalse(segments[1].hasConvertedDisplay)
        val loaded = mergeBunsetsuCandidates(segments[1], listOf(school.copy(string = "往く", length = 2u)))
        assertEquals("往く", loaded.displayText)
        assertFalse(loaded.candidates.any { it.string == "行く" })
        val fallback = mergeBunsetsuCandidates(segments[1], emptyList())
        assertEquals("いく", fallback.displayText)
        assertEquals(listOf("いく"), fallback.candidates.map { it.string })
        assertEquals("がっこうにいく", segments.joinToString("") { it.reading })
    }

    @Test fun partialNgWordAlsoInvalidatesProjectedDisplay() {
        ReflectionHelpers.setField(service, "isNgWordEnable", true)
        ReflectionHelpers.getField<MutableStateFlow<List<NgWord>>>(service, "_ngWordsList").value =
            listOf(NgWord(yomi = "", tango = "学校", matchMode = NgWordMatchMode.PARTIAL))
        val segments = projectSchoolSentence()
        assertFalse(segments[0].hasConvertedDisplay)
        assertEquals("がっこうに", segments[0].displayText)
        assertEquals("行く", segments[1].displayText)
    }

    @Test fun disabledNgWordsPreserveProjectedDisplay() {
        ReflectionHelpers.setField(service, "isNgWordEnable", false)
        ReflectionHelpers.getField<MutableStateFlow<List<NgWord>>>(service, "_ngWordsList").value =
            listOf(NgWord(yomi = "いく", tango = "行く", matchMode = NgWordMatchMode.EXACT))
        assertEquals(listOf("学校に", "行く"), projectSchoolSentence().map { it.displayText })
    }

    private fun template(text: String, score: Int) = UserTemplate(
        word = text, reading = "がっこう", posIndex = 0, posScore = score,
    )

    @Suppress("UNCHECKED_CAST")
    private fun projectSchoolSentence(): List<BunsetsuSegmentState> {
        val input = "がっこうにいく"
        val full = school.copy(string = "学校に行く", length = input.length.toUByte())
        val snapshot = BunsetsuConversionSnapshot(input, listOf(full), mapOf(full.string to listOf(
            CandidateConversionSegment(0, 4, "学校"),
            CandidateConversionSegment(4, 5, "に"),
            CandidateConversionSegment(5, 7, "行く"),
        )))
        return IMEService::class.java.getDeclaredMethod(
            "buildBunsetsuSegments", String::class.java, List::class.java, BunsetsuConversionSnapshot::class.java,
        ).apply { isAccessible = true }.invoke(service, input, listOf(5), snapshot) as List<BunsetsuSegmentState>
    }

    private suspend fun query(): KanaKanjiQueryResult = suspendCoroutineUninterceptedOrReturn { continuation ->
        IMEService::class.java.getDeclaredMethod(
            "queryBunsetsuConversion", String::class.java, Continuation::class.java,
        ).apply { isAccessible = true }.invoke(service, "がっこう", continuation)
    }
}
