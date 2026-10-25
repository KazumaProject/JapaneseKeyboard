package com.kazumaproject.markdownhelperkeyboard.gesture

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GestureConversionGateTest {
    private fun candidate(text: String) = Candidate(string = text, type = 1, length = 3u, score = 0)
    @Test fun previousStrokeCandidatesCannotBePreviewedForTheCurrentReading() = runTest {
        val input = MutableStateFlow("にほん")
        val results = MutableStateFlow<Pair<String, List<Candidate>>?>("にほ" to listOf(candidate("に補")))
        val pending = async { awaitGestureCandidates("にほん", input, results) }
        runCurrent()
        assertFalse(pending.isCompleted)
        val japan = listOf(candidate("日本"))
        results.value = "にほん" to japan
        runCurrent()
        assertEquals(japan, pending.await())
    }
    @Test fun editingTheReadingCancelsPendingConversion() = runTest {
        val input = MutableStateFlow("にほん")
        val results = MutableStateFlow<Pair<String, List<Candidate>>?>(null)
        val pending = async { awaitGestureCandidates("にほん", input, results) }
        runCurrent()
        input.value = "にほ"
        runCurrent()
        assertNull(pending.await())
    }
    @Test fun unavailableCandidatesTimeOutWithoutUsingOldResults() = runTest {
        val input = MutableStateFlow("にほん")
        val results = MutableStateFlow<Pair<String, List<Candidate>>?>("にほ" to listOf(candidate("に補")))
        val pending = async { awaitGestureCandidates("にほん", input, results) }
        advanceUntilIdle()
        assertNull(pending.await())
    }
}
