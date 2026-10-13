package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiConversionSession
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiQueryResult
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiQueryRequest
import com.kazumaproject.markdownhelperkeyboard.converter.session.CandidateQueryMode
import com.kazumaproject.core.domain.physical_keyboard.PhysicalCandidateCompositionSession
import com.kazumaproject.core.data.floating_candidate.CandidateItem
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.FloatingCandidateListAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateRefreshCoordinator
import com.kazumaproject.markdownhelperkeyboard.ime_service.models.CandidateShowFlag
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BunsetsuInitialSplitPositionsTest {
    private val service = IMEService()
    private val input = "あしたはとうきょうにいきます"
    private val first = Candidate("明日は東京に行きます", 1, input.length.toUByte(), 1000)
    private val second = first.copy(string = "明日は東京にいきます", score = 2000)
    private val result = BunsetsuCandidateResult(
        candidates = listOf(first, second),
        splitPatterns = listOf(listOf(4, 10), listOf(10)),
        splitPatternByCandidateString = mapOf(first.string to listOf(4, 10), second.string to listOf(10)),
    )

    @After fun closeDispatcher() {
        ReflectionHelpers.getField<ExecutorCoroutineDispatcher>(service, "kanaKanjiConversionDispatcher").close()
    }

    @Test fun rubyMetadataDoesNotRemoveTheFirstCandidatesSplitPositions() {
        val ruby = first.copy(conversionSegments = listOf(CandidateConversionSegment(0, input.length, first.string)))
        assertEquals(listOf(4, 10), resolve(listOf(ruby, second)))
    }

    @Test fun dictionaryDuplicateDoesNotRemoveTheMatchingEngineCandidatesSplitPositions() {
        val duplicate = first.copy(type = 20, score = 0, yomi = input)
        assertEquals(listOf(4, 10), resolve(listOf(duplicate, second)))
    }

    @Test fun promotedCandidateWithoutSplitMetadataUsesTheFirstRemainingEnginePath() {
        val promoted = first.copy(string = "あすは東京に行きます", score = 0)
        assertEquals(listOf(4, 10), resolve(listOf(promoted, first, second)))
        assertEquals(listOf(10), resolve(listOf(promoted, second)))
    }

    @Test fun reorderedEngineCandidateKeepsItsOwnPattern() {
        assertEquals(listOf(10), resolve(listOf(second, first)))
    }

    @Test fun explicitUnsplitPathIsNotReplacedByAnotherCandidatesPattern() {
        val unsplit = result.copy(splitPatternByCandidateString = result.splitPatternByCandidateString + (second.string to emptyList()))
        assertEquals(emptyList<Int>(), resolve(listOf(second, first), unsplit))
    }

    @Test fun missingPerCandidateMetadataUsesPrimaryPatternOnlyForTheEngineFirstCandidate() {
        val legacy = result.copy(splitPatternByCandidateString = emptyMap())
        assertEquals(listOf(4, 10), resolve(listOf(first, second), legacy))
        assertEquals(listOf(4, 10), resolve(listOf(second, first), legacy))
        assertEquals(emptyList<Int>(), resolve(listOf(second), legacy))
    }

    @Test fun missingOrFilteredEngineResultsAndEmptyCandidatesDoNotInventSplits() {
        assertEquals(emptyList<Int>(), resolve(emptyList()))
        assertEquals(emptyList<Int>(), resolve(listOf(first), null))
        assertEquals(emptyList<Int>(), resolve(listOf(first.copy(string = "独自候補"))))
        assertEquals(emptyList<Int>(), resolve(listOf(first.copy(length = 1u))))
    }

    @Test fun splitPositionsAreSanitizedBeforeReachingTheSpaceKeyHandlers() {
        val invalid = result.copy(splitPatternByCandidateString = mapOf(first.string to listOf(10, -1, 4, 4, input.length, 99)))
        assertEquals(listOf(4, 10), resolve(listOf(first), invalid))
    }

    @Test fun mergedStateRestoresSplittingWithAndWithoutCursorMove() {
        val promoted = first.copy(string = "あすは東京に行きます")
        for (cursorMove in listOf(false, true)) {
            ReflectionHelpers.setField(service, "bunsetsuSeparation", true)
            ReflectionHelpers.setField(service, "bunsetsuCursorMove", cursorMove)
            ReflectionHelpers.callInstanceMethod<Unit>(service, "updateBunsetsuStateAfterCandidateMerge",
                ReflectionHelpers.ClassParameter.from(String::class.java, input),
                ReflectionHelpers.ClassParameter.from(List::class.java, listOf(promoted, first, second)),
                ReflectionHelpers.ClassParameter.from(BunsetsuCandidateResult::class.java, result),
                ReflectionHelpers.ClassParameter.from(Map::class.java, emptyMap<String, List<CandidateConversionSegment>>()),
            )
            assertEquals(listOf(4, 10), ReflectionHelpers.getField<List<Int>>(service, "bunsetsuPositionList"))
            val snapshot = ReflectionHelpers.getField<BunsetsuConversionSnapshot>(service, "latestBunsetsuConversionSnapshot")
            assertEquals(listOf(4, 10), snapshot.initialSplitPositions)
            assertEquals(listOf("あしたは", "とうきょうに", "いきます"),
                buildConvertedBunsetsuSegments(input, snapshot.initialSplitPositions, snapshot).map { it.reading })
        }
    }

    @Test fun physicalSpaceSplitsTheReadingAndRequestsCandidatesForTheFirstBunsetsu() {
        preparePhysicalSplit()
        assertTrue(ReflectionHelpers.callInstanceMethod<Boolean>(service, "handleJapaneseSpaceFloating",
            ReflectionHelpers.ClassParameter.from(MainLayoutBinding::class.java, mock<MainLayoutBinding>()),
            ReflectionHelpers.ClassParameter.from(String::class.java, input),
            ReflectionHelpers.ClassParameter.from(List::class.java, emptyList<Any>()),
        ))
        assertEquals("あしたは", ReflectionHelpers.getField<MutableStateFlow<String>>(service, "_inputString").value)
        val session = ReflectionHelpers.getField<PhysicalCandidateCompositionSession>(service, "physicalCandidateCompositionSession")
        assertEquals("あしたは", session.queryText)
        assertEquals("とうきょうにいきます", session.trailingText)
        assertEquals(input, session.sourceText)
        assertEquals(session.generation, ReflectionHelpers.getField<Long>(service, "pendingPhysicalCandidatePreviewGeneration"))
        val refresh = ReflectionHelpers.getField<CandidateRefreshCoordinator>(service, "candidateRefreshCoordinator").requests.value
        assertEquals("あしたは", refresh.input)
        assertEquals(CandidateShowFlag.Updating, refresh.flag)
        assertTrue(ReflectionHelpers.getField<Boolean>(service, "henkanPressedWithBunsetsuDetect"))
        assertFalse(startPhysicalSplit()) // A second press must not split the existing tail again.
        assertEquals(input, session.sourceText)
    }

    @Test fun physicalSplitDoesNotRunWhenDisabledOrNoBoundaryExists() {
        preparePhysicalSplit()
        ReflectionHelpers.setField(service, "bunsetsuSeparation", false)
        assertFalse(startPhysicalSplit())
        ReflectionHelpers.setField(service, "bunsetsuSeparation", true)
        ReflectionHelpers.setField(service, "bunsetsuPositionList", listOf(0, input.length, 99))
        ReflectionHelpers.setField(service, "latestBunsetsuConversionSnapshot", snapshot(listOf(0, input.length, 99)))
        assertFalse(startPhysicalSplit())
        assertEquals(input, ReflectionHelpers.getField<MutableStateFlow<String>>(service, "_inputString").value)
    }

    @Test fun physicalContinuationSplitsWithoutLeavingThePreviousSessionBoundToTheWholeReading() {
        preparePhysicalSplit()
        val remaining = "とうきょうにいきます"
        val candidate = first.copy(string = "東京に行きます", length = remaining.length.toUByte())
        ReflectionHelpers.getField<MutableStateFlow<String>>(service, "_inputString").value = remaining
        ReflectionHelpers.setField(service, "bunsetsuPositionList", listOf(6))
        ReflectionHelpers.setField(service, "bunsetusMultipleDetect", true)
        ReflectionHelpers.getField<AtomicBoolean>(service, "isHenkan").set(true)
        ReflectionHelpers.setField(service, "latestBunsetsuConversionSnapshot", BunsetsuConversionSnapshot(
            remaining, listOf(candidate), emptyMap(), listOf(listOf(6)), listOf(6)))
        ReflectionHelpers.callInstanceMethod<Unit>(service, "beginPhysicalCandidateCompositionSession",
            ReflectionHelpers.ClassParameter.from(String::class.java, remaining))
        ReflectionHelpers.getField<AtomicBoolean>(service, "isHenkan").set(false)
        updateContinuation(remaining, candidate)
        assertEquals("Candidate refresh while typing must not start conversion", remaining, currentInput())
        ReflectionHelpers.getField<AtomicBoolean>(service, "isHenkan").set(true)
        updateContinuation(remaining, candidate)
        assertEquals("とうきょうに", currentInput())
        val session = currentPhysicalSession()!!
        assertEquals("とうきょうに", session.queryText)
        assertEquals("いきます", session.trailingText)
        assertEquals(remaining, session.sourceText)
        assertFalse(ReflectionHelpers.getField<Boolean>(service, "bunsetusMultipleDetect"))
    }

    private fun updateContinuation(remaining: String, candidate: Candidate) {
        ReflectionHelpers.callInstanceMethod<Unit>(service, "updateBunsetsuSpaceKeyIfNeeded",
            ReflectionHelpers.ClassParameter.from(MainLayoutBinding::class.java, mock<MainLayoutBinding>()),
            ReflectionHelpers.ClassParameter.from(List::class.java, listOf(candidate)),
            ReflectionHelpers.ClassParameter.from(String::class.java, remaining),
        )
    }

    @Test fun physicalSpaceQueriesCurrentInputWhenSnapshotBelongsToOlderInput() = runBlocking {
        preparePhysicalSplit()
        ReflectionHelpers.setField(service, "bunsetsuPositionList", listOf(2))
        ReflectionHelpers.setField(service, "latestBunsetsuConversionSnapshot", snapshot(listOf(2)).copy(input = "べつのよみ"))
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<KanaKanjiQueryResult>()
        installDelayedQuery(started, response)
        try {
            pressPhysicalSpace()
            assertEquals("Old split positions must not shorten the new input", input, currentInput())
            withTimeout(5000) { started.await() }
            val jobs = coroutineContext[Job]!!.children.toList()
            response.complete(KanaKanjiQueryResult(result.candidates, result))
            withTimeout(5000) { jobs.joinAll() }
            assertEquals("あしたは", currentInput())
            assertEquals("とうきょうにいきます", currentPhysicalSession()?.trailingText)
        } finally { response.cancel() }
    }

    @Test fun physicalQueryFinishingAfterCancellationDoesNotRestoreConversion() = runBlocking {
        preparePhysicalSplit()
        ReflectionHelpers.setField(service, "latestBunsetsuConversionSnapshot", null)
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<KanaKanjiQueryResult>()
        installDelayedQuery(started, response)
        try {
            pressPhysicalSpace()
            withTimeout(5000) { started.await() }
            val jobs = coroutineContext[Job]!!.children.toList()
            ReflectionHelpers.callInstanceMethod<Unit>(service, "clearPhysicalCandidateCompositionSession",
                ReflectionHelpers.ClassParameter.from(String::class.java, "conversion cancelled"))
            ReflectionHelpers.getField<AtomicBoolean>(service, "isHenkan").set(false)
            response.complete(KanaKanjiQueryResult(result.candidates, result))
            withTimeout(5000) { jobs.joinAll() }
            assertEquals(input, currentInput())
            assertEquals(null, currentPhysicalSession())
            assertEquals("", ReflectionHelpers.getField<AtomicReference<String>>(service, "stringInTail").get())
        } finally { response.cancel() }
    }

    @Test fun physicalUnsplitQueryRequestsANormalPreviewWithoutReusingThePreviousHighlight() = runBlocking {
        preparePhysicalSplit()
        ReflectionHelpers.setField(service, "latestBunsetsuConversionSnapshot", null)
        ReflectionHelpers.setField(service, "currentHighlightIndex", 5)
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<KanaKanjiQueryResult>()
        installDelayedQuery(started, response)
        try {
            pressPhysicalSpace()
            assertEquals(-1, ReflectionHelpers.getField<Int>(service, "currentHighlightIndex"))
            withTimeout(5000) { started.await() }
            val jobs = coroutineContext[Job]!!.children.toList()
            val unsplit = result.copy(splitPatternByCandidateString = result.splitPatternByCandidateString + (first.string to emptyList()))
            response.complete(KanaKanjiQueryResult(result.candidates, unsplit))
            withTimeout(5000) { jobs.joinAll() }
            assertEquals(input, currentInput())
            assertEquals("", currentPhysicalSession()?.trailingText)
            assertEquals(currentPhysicalSession()?.generation,
                ReflectionHelpers.getField<Long>(service, "pendingPhysicalCandidatePreviewGeneration"))
            val refresh = ReflectionHelpers.getField<CandidateRefreshCoordinator>(service, "candidateRefreshCoordinator").requests.value
            assertEquals(input, refresh.input)
            assertEquals(CandidateShowFlag.Updating, refresh.flag)
        } finally { response.cancel() }
    }

    @Test fun physicalQueryFromBeforeAnEditorMutationIsNotAppliedToTheSameReading() = runBlocking {
        preparePhysicalSplit()
        ReflectionHelpers.setField(service, "latestBunsetsuConversionSnapshot", null)
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<KanaKanjiQueryResult>()
        installDelayedQuery(started, response)
        try {
            pressPhysicalSpace()
            withTimeout(5000) { started.await() }
            val jobs = coroutineContext[Job]!!.children.toList()
            ReflectionHelpers.getField<EditorMutationRevision>(service, "editorMutationRevision").advance()
            response.complete(KanaKanjiQueryResult(result.candidates, result))
            withTimeout(5000) { jobs.joinAll() }
            assertEquals(input, currentInput())
            assertEquals("", ReflectionHelpers.getField<AtomicReference<String>>(service, "stringInTail").get())
        } finally { response.cancel() }
    }

    @Test fun physicalQueryFinishingAfterCandidateNavigationDoesNotOverrideTheSelection() = runBlocking {
        preparePhysicalSplit()
        ReflectionHelpers.setField(service, "latestBunsetsuConversionSnapshot", null)
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<KanaKanjiQueryResult>()
        installDelayedQuery(started, response)
        try {
            pressPhysicalSpace()
            withTimeout(5000) { started.await() }
            val jobs = coroutineContext[Job]!!.children.toList()
            ReflectionHelpers.setField(service, "fullSuggestionsList", listOf(CandidateItem(first.string, first.length)))
            ReflectionHelpers.callInstanceMethod<Unit>(service, "navigatePhysicalCandidate",
                ReflectionHelpers.ClassParameter.from(String::class.java, input),
                ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType!!, 1))
            response.complete(KanaKanjiQueryResult(result.candidates, result))
            withTimeout(5000) { jobs.joinAll() }
            assertEquals(input, currentInput())
            assertEquals(input, currentPhysicalSession()?.queryText)
            assertEquals(0, ReflectionHelpers.getField<Int>(service, "currentHighlightIndex"))
            assertEquals("", ReflectionHelpers.getField<AtomicReference<String>>(service, "stringInTail").get())
        } finally { response.cancel() }
    }

    private suspend fun installDelayedQuery(started: CompletableDeferred<Unit>, response: CompletableDeferred<KanaKanjiQueryResult>) {
        service.appPreference = mock()
        service.userDictionaryRepository = mock()
        service.userTemplateRepository = mock()
        ReflectionHelpers.setField(service, "isLearnDictionaryMode", false)
        ReflectionHelpers.setField(service, "isUserTemplateEnable", false)
        ReflectionHelpers.setField(service, "scope", CoroutineScope(coroutineContext))
        ReflectionHelpers.getField<CompletableDeferred<KanaKanjiEngine>>(service, "kanaKanjiEngineReady").complete(mock())
        val conversion = mock<KanaKanjiConversionSession>()
        whenever(conversion.query(any())).thenAnswer {
            val request = it.getArgument<KanaKanjiQueryRequest>(0)
            assertEquals(input, request.input)
            assertEquals(CandidateQueryMode.CONVERSION, request.mode)
            started.complete(Unit)
            runBlocking { withTimeout(5000) { response.await() } }
        }
        ReflectionHelpers.setField(service, "kanaKanjiConversionSession", conversion)
    }

    private fun snapshot(positions: List<Int> = listOf(4, 10)) = BunsetsuConversionSnapshot(
        input, result.candidates, emptyMap(), result.splitPatterns, positions)

    private fun currentInput() = ReflectionHelpers.getField<MutableStateFlow<String>>(service, "_inputString").value
    private fun currentPhysicalSession(): PhysicalCandidateCompositionSession? =
        ReflectionHelpers.getField(service, "physicalCandidateCompositionSession")

    private fun pressPhysicalSpace(): Boolean = ReflectionHelpers.callInstanceMethod(service, "handleJapaneseSpaceFloating",
        ReflectionHelpers.ClassParameter.from(MainLayoutBinding::class.java, mock<MainLayoutBinding>()),
        ReflectionHelpers.ClassParameter.from(String::class.java, input),
        ReflectionHelpers.ClassParameter.from(List::class.java, emptyList<Any>()),
    )

    private fun preparePhysicalSplit() {
        ReflectionHelpers.setField(service, "bunsetsuSeparation", true)
        ReflectionHelpers.setField(service, "bunsetsuCursorMove", false)
        ReflectionHelpers.setField(service, "bunsetsuPositionList", listOf(4, 10))
        ReflectionHelpers.setField(service, "latestBunsetsuConversionSnapshot", snapshot())
        ReflectionHelpers.getField<MutableSharedFlow<Boolean>>(service, "_physicalKeyboardEnable").tryEmit(true)
        ReflectionHelpers.getField<MutableStateFlow<String>>(service, "_inputString").value = input
        ReflectionHelpers.getField<AtomicReference<String>>(service, "stringInTail").set("")
        ReflectionHelpers.setField(service, "listAdapter", mock<FloatingCandidateListAdapter>())
    }

    private fun startPhysicalSplit(): Boolean = ReflectionHelpers.callInstanceMethod(service,
        "startPhysicalBunsetsuConversion", ReflectionHelpers.ClassParameter.from(String::class.java, input))

    private fun resolve(candidates: List<Candidate>, engineResult: BunsetsuCandidateResult? = result): List<Int> =
        ReflectionHelpers.callInstanceMethod(service, "resolveInitialBunsetsuSplitPositions",
            ReflectionHelpers.ClassParameter.from(String::class.java, input),
            ReflectionHelpers.ClassParameter.from(List::class.java, candidates),
            ReflectionHelpers.ClassParameter.from(BunsetsuCandidateResult::class.java, engineResult),
        )
}
