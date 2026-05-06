package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlideCandidateProvider
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlideTestFixtures
import com.kazumaproject.qwerty_keyboard.glide.QwertyInputPointers
import com.kazumaproject.qwerty_keyboard.glide.QwertyKeyboardProximityInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QwertyGlideInputCoordinatorTest {
    private val pointers = QwertyGlideTestFixtures.strokeFor("hello")
    private val proximityInfo = QwertyGlideTestFixtures.proximityInfo

    @Test
    fun previewDecodeReportsProcessingTrueThenFalse() = runTest {
        val requests = mutableListOf<CompletableDeferred<List<Candidate>>>()
        val events = mutableListOf<Boolean>()
        val previews = mutableListOf<List<Candidate>>()
        val coordinator = coordinator(
            scope = this,
            provider = deferredProvider(requests),
            events = events,
            previews = previews
        )

        coordinator.onQwertyGlideStarted()
        coordinator.onQwertyGlideUpdated(pointers, proximityInfo)
        advanceTimeBy(96)
        runCurrent()

        assertEquals(listOf(true), events)
        requests.single().complete(listOf(candidate("hello", 1000)))
        runCurrent()

        assertEquals(listOf(true, false), events)
        assertEquals(listOf("hello"), previews.single().map { it.string })
    }

    @Test
    fun finalDecodeReportsProcessingTrueThenFalse() = runTest {
        val requests = mutableListOf<CompletableDeferred<List<Candidate>>>()
        val events = mutableListOf<Boolean>()
        val finals = mutableListOf<List<Candidate>>()
        val coordinator = coordinator(
            scope = this,
            provider = deferredProvider(requests),
            events = events,
            finals = finals
        )

        coordinator.onQwertyGlideStarted()
        coordinator.onQwertyGlideEnded(pointers, proximityInfo)
        runCurrent()

        assertEquals(listOf(true), events)
        requests.single().complete(listOf(candidate("hello", 1000)))
        runCurrent()

        assertEquals(listOf(true, false), events)
        assertEquals(listOf("hello"), finals.single().map { it.string })
    }

    @Test
    fun cancelDuringDecodeReportsProcessingFalse() = runTest {
        val requests = mutableListOf<CompletableDeferred<List<Candidate>>>()
        val events = mutableListOf<Boolean>()
        val coordinator = coordinator(
            scope = this,
            provider = deferredProvider(requests),
            events = events
        )

        coordinator.onQwertyGlideStarted()
        coordinator.onQwertyGlideEnded(pointers, proximityInfo)
        runCurrent()
        coordinator.cancelPending()
        runCurrent()

        assertEquals(listOf(true, false), events)
        requests.single().complete(listOf(candidate("hello", 1000)))
        runCurrent()
        assertEquals(listOf(true, false), events)
    }

    @Test
    fun cancelledPreviewDoesNotHideNewFinalProcessing() = runTest {
        val requests = mutableListOf<CompletableDeferred<List<Candidate>>>()
        val events = mutableListOf<Boolean>()
        val coordinator = coordinator(
            scope = this,
            provider = deferredProvider(requests),
            events = events
        )

        coordinator.onQwertyGlideStarted()
        coordinator.onQwertyGlideUpdated(pointers, proximityInfo)
        advanceTimeBy(96)
        runCurrent()
        assertEquals(listOf(true), events)

        coordinator.onQwertyGlideEnded(pointers, proximityInfo)
        runCurrent()
        assertEquals(2, requests.size)
        assertEquals(listOf(true), events)

        requests[1].complete(listOf(candidate("hello", 1000)))
        runCurrent()
        assertEquals(listOf(true, false), events)
    }

    @Test
    fun emptyCandidatesStillClearProcessing() = runTest {
        val events = mutableListOf<Boolean>()
        val finals = mutableListOf<List<Candidate>>()
        val coordinator = coordinator(
            scope = this,
            provider = immediateProvider(emptyList()),
            events = events,
            finals = finals
        )

        coordinator.onQwertyGlideStarted()
        coordinator.onQwertyGlideEnded(pointers, proximityInfo)
        runCurrent()

        assertEquals(listOf(true, false), events)
        assertTrue(finals.single().isEmpty())
    }

    @Test
    fun decodeExceptionStillClearsProcessing() = runTest {
        val events = mutableListOf<Boolean>()
        val coordinator = coordinator(
            scope = this,
            provider = throwingProvider(IllegalStateException("decode failed")),
            events = events
        )

        coordinator.onQwertyGlideStarted()
        coordinator.onQwertyGlideEnded(pointers, proximityInfo)
        runCurrent()

        assertEquals(listOf(true, false), events)
    }

    @Test
    fun cancellationExceptionStillClearsProcessing() = runTest {
        val events = mutableListOf<Boolean>()
        val coordinator = coordinator(
            scope = this,
            provider = throwingProvider(CancellationException("decode cancelled")),
            events = events
        )

        coordinator.onQwertyGlideStarted()
        coordinator.onQwertyGlideEnded(pointers, proximityInfo)
        runCurrent()

        assertEquals(listOf(true, false), events)
    }

    private fun coordinator(
        scope: TestScope,
        provider: QwertyGlideCandidateProvider,
        events: MutableList<Boolean>,
        previews: MutableList<List<Candidate>> = mutableListOf(),
        finals: MutableList<List<Candidate>> = mutableListOf()
    ): QwertyGlideInputCoordinator {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return QwertyGlideInputCoordinator(
            scope = scope,
            candidateProvider = provider,
            previousTextProvider = { "" },
            onPreviewCandidates = previews::add,
            onFinalCandidates = finals::add,
            onProcessingChanged = events::add,
            decodeDispatcher = dispatcher
        )
    }

    private fun deferredProvider(
        requests: MutableList<CompletableDeferred<List<Candidate>>>
    ): QwertyGlideCandidateProvider {
        return object : QwertyGlideCandidateProvider {
            override suspend fun getGlideCandidates(
                inputPointers: QwertyInputPointers,
                proximityInfo: QwertyKeyboardProximityInfo,
                previousText: String,
                limit: Int
            ): List<Candidate> {
                val deferred = CompletableDeferred<List<Candidate>>()
                requests += deferred
                return deferred.await()
            }
        }
    }

    private fun immediateProvider(candidates: List<Candidate>): QwertyGlideCandidateProvider {
        return object : QwertyGlideCandidateProvider {
            override suspend fun getGlideCandidates(
                inputPointers: QwertyInputPointers,
                proximityInfo: QwertyKeyboardProximityInfo,
                previousText: String,
                limit: Int
            ): List<Candidate> = candidates
        }
    }

    private fun throwingProvider(error: Throwable): QwertyGlideCandidateProvider {
        return object : QwertyGlideCandidateProvider {
            override suspend fun getGlideCandidates(
                inputPointers: QwertyInputPointers,
                proximityInfo: QwertyKeyboardProximityInfo,
                previousText: String,
                limit: Int
            ): List<Candidate> {
                throw error
            }
        }
    }

    private fun candidate(string: String, score: Int): Candidate {
        return Candidate(
            string = string,
            type = 36.toByte(),
            length = string.length.toUByte(),
            score = score
        )
    }
}
