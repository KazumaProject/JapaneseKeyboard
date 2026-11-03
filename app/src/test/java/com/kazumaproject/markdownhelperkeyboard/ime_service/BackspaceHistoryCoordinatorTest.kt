package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackspaceHistoryCoordinatorTest {
    @Test fun recordsActualSelectionAndUnicodeDeletionAndWaitsBeforeUndo() = runTest {
        var editorText = "a👩‍👩‍👧‍👦b"
        var start = 1
        var end = editorText.length - 1
        var revision = 0L
        val editor = mock<InputConnection>()
        whenever(editor.getExtractedText(any(), eq(0))).thenAnswer {
            ExtractedText().apply { text = editorText; startOffset = 0; partialStartOffset = -1; selectionStart = start; selectionEnd = end }
        }
        val recorded = mutableListOf<Pair<String, Long?>>()
        val coordinator = BackspaceHistoryCoordinator(this, { editor }, { revision }, { 1L }, { true }, {
            editorText = editorText.removeRange(start, end); end = start; revision++
        }, { value, group -> recorded.add(value to group) }, UnconfinedTestDispatcher(testScheduler))
        var historyAtUndo: List<Pair<String, Long?>>? = null
        coordinator.enqueue(4L)
        coordinator.afterPending { historyAtUndo = recorded.toList() }
        advanceUntilIdle()
        assertEquals("ab", editorText)
        assertEquals(listOf("👩‍👩‍👧‍👦" to 4L), recorded)
        assertEquals(recorded, historyAtUndo)
    }

    @Test fun ignoredOrUnreadableEditorNeverCreatesAnInventedHistoryEntry() = runTest {
        val editor = mock<InputConnection>()
        var reads = 0
        whenever(editor.getExtractedText(any(), eq(0))).thenAnswer {
            reads++
            ExtractedText().apply { text = "ab"; startOffset = 0; partialStartOffset = -1; selectionStart = 2; selectionEnd = 2 }
        }
        var deletes = 0
        val recorded = mutableListOf<String>()
        val coordinator = BackspaceHistoryCoordinator(this, { editor }, { 0L }, { 1L }, { true },
            { deletes++ }, { value, _ -> recorded.add(value) }, UnconfinedTestDispatcher(testScheduler))
        repeat(3) { coordinator.enqueue() }
        advanceUntilIdle()
        assertEquals(3, deletes)
        assertTrue(reads > 1)
        assertTrue(recorded.isEmpty())
    }

    @Test fun unreadableEditorStillReceivesEveryQueuedDeletion() = runTest {
        val editor = mock<InputConnection>()
        whenever(editor.getExtractedText(any(), eq(0))).thenReturn(null)
        var deletes = 0
        var invalidations = 0
        val coordinator = BackspaceHistoryCoordinator(
            this, { editor }, { 0L }, { 1L }, { true }, { deletes++ },
            { _, _ -> fail("unreadable deletion cannot be recorded") },
            UnconfinedTestDispatcher(testScheduler), invalidateHistory = { invalidations++ }
        )
        repeat(3) { coordinator.enqueue() }
        advanceUntilIdle()
        assertEquals(3, deletes)
        assertEquals(1, invalidations)
    }

    @Test fun laterKeyFlushesUnissuedBackspacesWithoutInventingHistory() = runTest {
        val editor = mock<InputConnection>()
        var deletes = 0
        var invalidations = 0
        val coordinator = BackspaceHistoryCoordinator(
            this, { editor }, { 0L }, { 1L }, { true }, { deletes++ },
            { _, _ -> fail("flushed deletion cannot be recorded") },
            StandardTestDispatcher(testScheduler), invalidateHistory = { invalidations++ }
        )
        coordinator.enqueue()
        coordinator.enqueue()
        coordinator.flushPendingAndCancel()
        advanceUntilIdle()
        assertEquals(2, deletes)
        assertEquals(1, invalidations)
    }

    @Test fun flushingDoesNotRepeatAnAlreadyIssuedDeletion() = runTest {
        val editor = mock<InputConnection>()
        whenever(editor.getExtractedText(any(), eq(0))).thenReturn(ExtractedText().apply {
            text = "abc"; startOffset = 0; partialStartOffset = -1; selectionStart = 3; selectionEnd = 3
        })
        var deletes = 0
        val coordinator = BackspaceHistoryCoordinator(
            this, { editor }, { 0L }, { 1L }, { true }, { deletes++ },
            { _, _ -> fail("unacknowledged deletion cannot be recorded") },
            StandardTestDispatcher(testScheduler)
        )
        coordinator.enqueue()
        runCurrent()
        assertEquals(1, deletes)
        coordinator.enqueue()
        coordinator.enqueue()
        coordinator.flushPendingAndCancel()
        advanceUntilIdle()
        assertEquals(3, deletes)
    }

    @Test fun changedInputSessionDropsPendingHistory() = runTest {
        val editor = mock<InputConnection>()
        var session = 1L
        whenever(editor.getExtractedText(any(), eq(0))).thenAnswer {
            session = 2L
            ExtractedText().apply { text = "a"; startOffset = 0; partialStartOffset = -1; selectionStart = 1; selectionEnd = 1 }
        }
        var deletes = 0
        val recorded = mutableListOf<String>()
        val coordinator = BackspaceHistoryCoordinator(this, { editor }, { 0L }, { session }, { true },
            { deletes++ }, { value, _ -> recorded.add(value) }, UnconfinedTestDispatcher(testScheduler))
        coordinator.enqueue()
        advanceUntilIdle()
        assertEquals(0, deletes)
        assertTrue(recorded.isEmpty())
    }

    @Test fun externalSelectionChangeDuringReadCancelsTheOldDeletion() = runTest {
        val editor = mock<InputConnection>()
        var selectionRevision = 0L
        whenever(editor.getExtractedText(any(), eq(0))).thenAnswer {
            selectionRevision++
            ExtractedText().apply { text = "abcd"; startOffset = 0; partialStartOffset = -1; selectionStart = 4; selectionEnd = 4 }
        }
        var deletes = 0
        val coordinator = BackspaceHistoryCoordinator(this, { editor }, { 0L }, { 1L }, { true },
            { deletes++ }, { _, _ -> fail("stale deletion must not be recorded") },
            UnconfinedTestDispatcher(testScheduler), { selectionRevision }, { 1 to 1 })
        coordinator.enqueue()
        advanceUntilIdle()
        assertEquals(0, deletes)
    }
}
