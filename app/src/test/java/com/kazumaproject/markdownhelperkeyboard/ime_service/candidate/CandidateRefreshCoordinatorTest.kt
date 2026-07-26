package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.ime_service.models.CandidateShowFlag
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateRefreshCoordinatorTest {

    @Test
    fun repeatedUpdatingRequestsRemainDistinctAndCarryTheirInputSnapshot() {
        val coordinator = CandidateRefreshCoordinator()

        val first = coordinator.request("あ", CandidateShowFlag.Updating)
        val second = coordinator.request("あい", CandidateShowFlag.Updating)

        assertEquals(first.sessionId, second.sessionId)
        assertTrue(second.revision > first.revision)
        assertEquals("あい", coordinator.requests.value.input)
        assertEquals(CandidateShowFlag.Updating, coordinator.requests.value.flag)
        assertFalse(coordinator.isCurrent(first))
        assertTrue(coordinator.isCurrent(second))
    }

    @Test
    fun restartInvalidatesRequestsFromThePreviousEditorSession() {
        val coordinator = CandidateRefreshCoordinator()
        val oldRequest = coordinator.request("ふるい", CandidateShowFlag.Updating)

        val restarted = coordinator.restart()

        assertTrue(restarted.sessionId > oldRequest.sessionId)
        assertEquals(CandidateShowFlag.Idle, restarted.flag)
        assertEquals("", restarted.input)
        assertFalse(coordinator.isCurrent(oldRequest))
        assertTrue(coordinator.isCurrent(restarted))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun latestRequestCancelsOnlyCandidateWorkAndDoesNotBlockProducer() = runTest {
        val coordinator = CandidateRefreshCoordinator()
        val firstStarted = CompletableDeferred<Unit>()
        val completedInputs = mutableListOf<String>()
        val collector = launch {
            coordinator.requests.collectLatest { request ->
                if (request.flag == CandidateShowFlag.Idle) return@collectLatest
                if (request.input == "あ") {
                    firstStarted.complete(Unit)
                    CompletableDeferred<Unit>().await()
                }
                completedInputs += request.input
            }
        }

        coordinator.request("あ", CandidateShowFlag.Updating)
        firstStarted.await()
        repeat(10_000) { index ->
            coordinator.request("あ$index", CandidateShowFlag.Updating)
        }
        advanceUntilIdle()

        assertEquals(listOf("あ9999"), completedInputs)
        collector.cancel()
    }
}
