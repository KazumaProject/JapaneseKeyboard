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
    private fun snapshot(text: CharSequence, caret: Int, offset: Int = 0) = ExtractedText().apply {
        this.text = text; startOffset = offset; selectionStart = caret; selectionEnd = caret; partialStartOffset = -1
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

    @Test fun latestComposingWriteAheadOfTheNativeRangePreservesQueuedInput() = runTest {
        val editor = mock<InputConnection>()
        // Editors such as Compose return unstyled text. The native range callback can
        // still describe the previous one-character write when the latest text is read.
        whenever(editor.getExtractedText(any(), eq(0))).thenReturn(snapshot("ああ", 2))
        var preedit = "ああ"
        val events = mutableListOf<String>()
        val gate = CursorInputSynchronization(this, { editor }, { 1L }, Mutex(), { 0..1 },
            StandardTestDispatcher(testScheduler), { CursorComposingExpectation(0, "ああ") })
        gate.verifyBeforeInput(1, 1) { _, _ -> preedit = ""; events += "cleared" }
        assertTrue(gate.defer({ true }) { events += "input:$preedit" })
        advanceUntilIdle()
        assertEquals(listOf("input:ああ"), events)
    }

    @Test fun staleNoCompositionNotificationMatchingTheLatestCaretPreservesPreedit() = runTest {
        val editor = mock<InputConnection>()
        whenever(editor.getExtractedText(any(), eq(0))).thenReturn(snapshot("ああ", 2))
        var cleared = false
        val gate = CursorInputSynchronization(this, { editor }, { 1L }, Mutex(), { 0..1 },
            StandardTestDispatcher(testScheduler), { CursorComposingExpectation(0, "ああ") })
        gate.verifyNotification(2, 2) { _, _ -> cleared = true }
        advanceUntilIdle()
        assertFalse(cleared)
    }

    @Test fun styledNativeCompletionInsideThePreviousRangeAllowsFreshInput() = runTest {
        val editor = mock<InputConnection>()
        // A real styled extraction without composing spans distinguishes completion
        // from style stripping even if the app moves the caret inside the old range.
        whenever(editor.getExtractedText(any(), eq(0))).thenReturn(snapshot(SpannableString("ああ"), 0))
        var preedit = "ああ"
        val events = mutableListOf<String>()
        val gate = CursorInputSynchronization(this, { editor }, { 1L }, Mutex(), { 0..1 },
            StandardTestDispatcher(testScheduler), { CursorComposingExpectation(0, "ああ") })
        gate.verifyBeforeInput(2, 2) { start, _ -> preedit = ""; events += "selection:$start" }
        assertTrue(gate.defer({ true }) { events += "input:$preedit" })
        advanceUntilIdle()
        assertEquals(listOf("selection:0", "input:"), events)
    }

    @Test fun unstyledExternalCaretOutsideTheLatestWriteAllowsFreshInput() = runTest {
        val editor = mock<InputConnection>()
        whenever(editor.getExtractedText(any(), eq(0))).thenReturn(snapshot("abcdef", 0))
        var preedit = "cd"
        val events = mutableListOf<String>()
        val gate = CursorInputSynchronization(this, { editor }, { 1L }, Mutex(), { 2..3 },
            StandardTestDispatcher(testScheduler), { CursorComposingExpectation(2, "cd") })
        gate.verifyBeforeInput(3, 3) { start, _ -> preedit = ""; events += "selection:$start" }
        assertTrue(gate.defer({ true }) { events += "input:$preedit" })
        advanceUntilIdle()
        assertEquals(listOf("selection:0", "input:"), events)
    }

    @Test fun transformedNativeTextInsideTheLatestWritePreservesPreedit() = runTest {
        val editor = mock<InputConnection>()
        // InputFilter.AllCaps can transform an accepted composing write without ending it.
        whenever(editor.getExtractedText(any(), eq(0))).thenReturn(snapshot("AA", 2))
        var cleared = false
        val gate = CursorInputSynchronization(this, { editor }, { 1L }, Mutex(), { 0..1 },
            StandardTestDispatcher(testScheduler), { CursorComposingExpectation(0, "aa") })
        gate.verifyBeforeInput(1, 1) { _, _ -> cleared = true }
        advanceUntilIdle()
        assertFalse(cleared)
    }

    @Test fun unpositionedLatestWriteCannotBeDiscardedFromUnstyledText() = runTest {
        val editor = mock<InputConnection>()
        whenever(editor.getExtractedText(any(), eq(0))).thenReturn(snapshot("ああ", 2))
        var cleared = false
        val gate = CursorInputSynchronization(this, { editor }, { 1L }, Mutex(), { null },
            StandardTestDispatcher(testScheduler), { CursorComposingExpectation(null, "ああ") })
        gate.verifyBeforeInput(-1, -1) { _, _ -> cleared = true }
        advanceUntilIdle()
        assertFalse(cleared)
    }

    @Test fun unstyledSurroundingTextCannotConfirmAStaleSelectionNotification() = runTest {
        val editor = mock<InputConnection>()
        whenever(editor.getTextBeforeCursor(eq(1), eq(InputConnection.GET_TEXT_WITH_STYLES))).thenReturn("あ")
        whenever(editor.getTextAfterCursor(eq(1), eq(InputConnection.GET_TEXT_WITH_STYLES))).thenReturn("")
        var cleared = false
        val gate = CursorInputSynchronization(this, { editor }, { 1L }, Mutex(), { 0..1 },
            StandardTestDispatcher(testScheduler), { CursorComposingExpectation(0, "ああ") })
        gate.verifyNotification(0, 0) { _, _ -> cleared = true }
        advanceUntilIdle()
        assertFalse(cleared)
    }

    @Test fun latestWriteUsesAbsoluteOffsetsInAWindowedExtraction() = runTest {
        val editor = mock<InputConnection>()
        whenever(editor.getExtractedText(any(), eq(0))).thenReturn(snapshot("XああY", 3, offset = 10))
        var cleared = false
        val gate = CursorInputSynchronization(this, { editor }, { 1L }, Mutex(), { 11..12 },
            StandardTestDispatcher(testScheduler), { CursorComposingExpectation(11, "ああ") })
        gate.verifyBeforeInput(12, 12) { _, _ -> cleared = true }
        advanceUntilIdle()
        assertFalse(cleared)
    }

    @Test fun aFailedSurroundingReadCannotEstablishThatComposingSpansAreAbsent() = runTest {
        val editor = mock<InputConnection>()
        // The successful side is styled, but the other read failed rather than proving
        // that the editor has no composing text on that side of its current caret.
        whenever(editor.getTextBeforeCursor(eq(1), eq(InputConnection.GET_TEXT_WITH_STYLES)))
            .thenReturn(SpannableString("あ"))
        var cleared = false
        val gate = CursorInputSynchronization(this, { editor }, { 1L }, Mutex(), { 0..1 },
            StandardTestDispatcher(testScheduler), { CursorComposingExpectation(0, "ああ") })
        gate.verifyBeforeInput(1, 1) { _, _ -> cleared = true }
        advanceUntilIdle()
        assertFalse(cleared)
    }

    @Test fun incompleteUnstyledExtractionCannotDiscardTheLatestComposingWrite() = runTest {
        val editor = mock<InputConnection>()
        whenever(editor.getExtractedText(any(), eq(0))).thenReturn(snapshot("ああ", 2, offset = 3))
        var cleared = false
        val gate = CursorInputSynchronization(this, { editor }, { 1L }, Mutex(), { 0..4 },
            StandardTestDispatcher(testScheduler), { CursorComposingExpectation(0, "abcああ") })
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
