package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.core.domain.physical_keyboard.PhysicalCandidateCompositionSession
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.FloatingCandidateListAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateRefreshCoordinator
import com.kazumaproject.markdownhelperkeyboard.ime_service.models.CandidateShowFlag
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.atomic.AtomicReference

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
        assertTrue(startPhysicalSplit())
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
        assertFalse(startPhysicalSplit())
        assertEquals(input, ReflectionHelpers.getField<MutableStateFlow<String>>(service, "_inputString").value)
    }

    private fun preparePhysicalSplit() {
        ReflectionHelpers.setField(service, "bunsetsuSeparation", true)
        ReflectionHelpers.setField(service, "bunsetsuCursorMove", false)
        ReflectionHelpers.setField(service, "bunsetsuPositionList", listOf(4, 10))
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
