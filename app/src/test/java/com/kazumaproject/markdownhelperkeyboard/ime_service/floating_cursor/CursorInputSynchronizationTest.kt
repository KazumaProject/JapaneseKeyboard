package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor

import android.text.SpannableString
import android.text.Spanned
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class CursorInputSynchronizationTest {
    private fun snapshot(text: CharSequence, caret: Int) = ExtractedText().apply {
        this.text = text; startOffset = 0; selectionStart = caret; selectionEnd = caret; partialStartOffset = -1
    }

    @Test fun staleSelectionNotificationCannotDiscardNewNativeComposition() = runTest {
        val editor = mock<InputConnection>()
        val text = SpannableString("abcあ").apply { setSpan(Any(), 3, 4, Spanned.SPAN_COMPOSING) }
        whenever(editor.getExtractedText(any(), eq(0))).thenReturn(snapshot(text, 4))
        var cleared = false
        val gate = CursorInputSynchronization(this, { editor }, { 0L }, Mutex(), { null }, StandardTestDispatcher(testScheduler))
        gate.verifyNotification(3, 3) { _, _ -> cleared = true }
        advanceUntilIdle()
        assertFalse(cleared)
    }

    @Test fun verifiedExternalMoveIsAppliedBeforeQueuedInputInArrivalOrder() = runTest {
        val editor = mock<InputConnection>()
        whenever(editor.getExtractedText(any(), eq(0))).thenReturn(snapshot("abcdef", 0))
        val events = mutableListOf<String>()
        val gate = CursorInputSynchronization(this, { editor }, { 0L }, Mutex(), { 2..4 }, StandardTestDispatcher(testScheduler))
        gate.verifyBeforeInput(4, 4) { start, _ -> events += "selection:$start" }
        assertTrue(gate.defer({ true }) { events += "down" })
        assertTrue(gate.defer({ true }) { events += "up" })
        advanceUntilIdle()
        assertEquals(listOf("selection:0", "down", "up"), events)
    }

    @Test fun unreadableEditorIsNotEvidenceThatCompositionEnded() = runTest {
        val editor = mock<InputConnection>()
        var cleared = false
        val gate = CursorInputSynchronization(this, { editor }, { 0L }, Mutex(), { null }, StandardTestDispatcher(testScheduler))
        gate.verifyNotification(0, 0) { _, _ -> cleared = true }
        advanceUntilIdle()
        assertFalse(cleared)
    }

    @Test fun styledTextStrippingWithinTheKnownRangePreservesPreedit() = runTest {
        val editor = mock<InputConnection>()
        whenever(editor.getExtractedText(any(), eq(0))).thenReturn(snapshot("abcあ", 4))
        var cleared = false
        val gate = CursorInputSynchronization(this, { editor }, { 0L }, Mutex(), { 3..4 }, StandardTestDispatcher(testScheduler))
        gate.verifyBeforeInput(4, 4) { _, _ -> cleared = true }
        advanceUntilIdle()
        assertFalse(cleared)
    }

    @Test fun writesAndInputTargetChangesDiscardOlderChecksAndInput() = runTest {
        val editor = mock<InputConnection>()
        var revision = 0L
        whenever(editor.getExtractedText(any(), eq(0))).thenAnswer { revision++; snapshot("abc", 0) }
        var cleared = false
        var inserted = false
        val gate = CursorInputSynchronization(this, { editor }, { revision }, Mutex(), { null }, StandardTestDispatcher(testScheduler))
        gate.verifyNotification(0, 0) { _, _ -> cleared = true }
        gate.defer({ false }) { inserted = true }
        advanceUntilIdle()
        assertFalse(cleared)
        assertFalse(inserted)
    }

    @Test fun resetDiscardsOldWorkerWithoutClearingTheNewSessionsQueue() = runTest {
        val editor = mock<InputConnection>()
        whenever(editor.getExtractedText(any(), eq(0))).thenReturn(snapshot("abc", 0))
        val events = mutableListOf<String>()
        val gate = CursorInputSynchronization(this, { editor }, { 0L }, Mutex(), { null }, StandardTestDispatcher(testScheduler))
        gate.verifyBeforeInput(0, 0) { _, _ -> }
        gate.defer({ true }) { events += "old" }
        gate.reset()
        gate.verifyBeforeInput(0, 0) { _, _ -> }
        gate.defer({ true }) { events += "new" }
        advanceUntilIdle()
        assertEquals(listOf("new"), events)
    }
}
