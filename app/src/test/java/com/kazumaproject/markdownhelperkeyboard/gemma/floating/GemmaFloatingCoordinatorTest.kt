package com.kazumaproject.markdownhelperkeyboard.gemma.floating

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GemmaFloatingCoordinatorTest {
    private fun input(text: String, session: Long = 1, revision: Long = 1) =
        GemmaFloatingInput(session, revision, text, GemmaFloatingSettings.defaults().first())

    @Test fun processingStartsAfterDebounceAndIsReplacedByTheResult() = runTest {
        val response = CompletableDeferred<String>()
        val events = mutableListOf<String?>()
        val request = input("hello")
        val controller = GemmaFloatingCoordinator(this, { response.await() }, {},
            { _, result -> events += result }, publishProcessing = {
                assertEquals(request, it)
                events += "processing"
            })
        controller.update(request)
        runCurrent(); advanceTimeBy(299); runCurrent()
        assertEquals(listOf<String?>(null), events)
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(null, "processing"), events)
        response.complete("こんにちは"); runCurrent()
        assertEquals(listOf(null, "processing", "こんにちは"), events)
        controller.destroy()
    }

    @Test fun processingClearsOnFailureCancellationAndForegroundSuspension() = runTest {
        val responses = List(3) { CompletableDeferred<String>() }
        var calls = 0
        val events = mutableListOf<String?>()
        val controller = GemmaFloatingCoordinator(this, { responses[calls++].await() }, {},
            { _, result -> events += result }, publishProcessing = { events += "processing" })
        controller.update(input("first")); runCurrent(); advanceTimeBy(300); runCurrent()
        assertEquals("processing", events.last())
        responses[0].completeExceptionally(IllegalStateException("generation failed")); runCurrent()
        assertNull(events.last())
        controller.update(input("second")); runCurrent(); advanceTimeBy(300); runCurrent()
        assertEquals("processing", events.last())
        controller.suspendForForeground()
        assertNull(events.last())
        responses[1].complete("obsolete"); runCurrent()
        assertNull(events.last())
        controller.update(input("third")); runCurrent(); advanceTimeBy(300); runCurrent()
        assertEquals("processing", events.last())
        controller.update(null)
        assertNull(events.last())
        responses[2].complete("obsolete"); runCurrent()
        assertNull(events.last())
        controller.destroy()
    }

    @Test fun rapidChangesDebounceToTheLatestInput() = runTest {
        val requests = mutableListOf<String>()
        val results = mutableListOf<String>()
        val controller = GemmaFloatingCoordinator(this, { requests += it.text; "result" }, {}, { _, result -> result?.let(results::add) })
        controller.update(input("a")); runCurrent(); advanceTimeBy(150)
        controller.update(input("ab")); advanceTimeBy(150); runCurrent()
        assertTrue(requests.isEmpty())
        advanceTimeBy(149); runCurrent()
        assertTrue(requests.isEmpty())
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf("ab"), requests)
        assertEquals(listOf("result"), results)
        controller.destroy()
    }

    @Test fun changedNbestCandidatesInvalidateAnInFlightResultEvenWhenComposingTextIsUnchanged() = runTest {
        val first = CompletableDeferred<String>()
        val results = mutableListOf<String>()
        var requests = 0
        val controller = GemmaFloatingCoordinator(this, {
            if (++requests == 1) first.await() else "new candidate"
        }, {}, { _, text -> text?.let(results::add) })
        val original = input("はし").copy(rule = null,
            nbest = GemmaNbestRequest("はし", "", listOf("箸", "橋")))
        controller.update(original)
        runCurrent(); advanceTimeBy(300); runCurrent()
        controller.update(original.copy(nbest = original.nbest!!.copy(candidates = listOf("箸", "端"))))
        advanceTimeBy(300); runCurrent()
        first.complete("stale candidate"); runCurrent()
        assertEquals(listOf("new candidate"), results)
        controller.destroy()
    }

    @Test fun nativeInferenceStaysSerialAndDropsStaleResultsAcrossSessions() = runTest {
        val first = CompletableDeferred<String>()
        val requests = mutableListOf<String>()
        val results = mutableListOf<String>()
        var cancellations = 0
        val controller = GemmaFloatingCoordinator(this, {
            requests += it.text
            if (requests.size == 1) first.await() else "new result"
        }, { cancellations++ }, { _, result -> result?.let(results::add) })
        controller.update(input("old")); runCurrent(); advanceTimeBy(300); runCurrent()
        controller.update(input("middle")); controller.update(input("new", session = 2))
        advanceTimeBy(1000); runCurrent()
        assertEquals(listOf("old"), requests)
        first.complete("stale result"); runCurrent(); advanceTimeBy(300); runCurrent()
        assertEquals(listOf("old", "new"), requests)
        assertEquals(listOf("new result"), results)
        assertEquals(2, cancellations)
        controller.destroy()
    }

    @Test fun foregroundActionSuppressesPreviewUntilInputChanges() = runTest {
        val result = CompletableDeferred<String>()
        val results = mutableListOf<String>()
        val controller = GemmaFloatingCoordinator(this, { result.await() }, {}, { _, value -> value?.let(results::add) })
        controller.update(input("old")); runCurrent(); advanceTimeBy(300); runCurrent()
        controller.suspendForForeground(); result.complete("ignored"); runCurrent()
        assertTrue(results.isEmpty())
        controller.update(input("new")); runCurrent(); advanceTimeBy(300); runCurrent()
        assertEquals(listOf("ignored"), results)
        controller.destroy()
    }

    @Test fun disablingDuringGenerationPreventsPublicationAndBusyEngineRetries() = runTest {
        val response = CompletableDeferred<String>()
        var calls = 0
        val results = mutableListOf<String>()
        val controller = GemmaFloatingCoordinator(this, { if (++calls == 1) null else response.await() }, {}, { _, value -> value?.let(results::add) })
        controller.update(input("text")); runCurrent(); advanceTimeBy(600); runCurrent()
        assertEquals(2, calls)
        controller.update(null); response.complete("obsolete"); runCurrent()
        assertTrue(results.isEmpty())
        controller.destroy()
    }

    @Test fun generationTimeoutDoesNotKillFutureSuggestions() = runTest {
        var calls = 0
        val results = mutableListOf<String>()
        val controller = GemmaFloatingCoordinator(this, {
            if (++calls == 1) withTimeout(10) { delay(100); "too late" } else "recovered"
        }, {}, { _, value -> value?.let(results::add) })
        controller.update(input("old")); runCurrent(); advanceTimeBy(400); runCurrent()
        controller.update(input("new")); runCurrent(); advanceTimeBy(300); runCurrent()
        assertEquals(listOf("recovered"), results)
        controller.destroy()
    }
}
