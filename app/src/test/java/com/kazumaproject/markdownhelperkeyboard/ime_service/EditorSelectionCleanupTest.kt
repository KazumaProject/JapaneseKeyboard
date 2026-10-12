package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.text.SpannableString
import android.text.Spanned
import android.text.style.UnderlineSpan
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.coroutines.CoroutineContext

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
@OptIn(ExperimentalCoroutinesApi::class)
class EditorSelectionCleanupTest {
    private class QueuedReads : CoroutineDispatcher() {
        val tasks = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }
        fun reply() { tasks.removeFirst().run() }
    }

    private class Editor(scope: TestScope) {
        val connection: InputConnection = mock()
        var active: InputConnection? = connection
        var revision = 0L
        val reads = QueuedReads()
        var extracted: ExtractedText? = ExtractedText().apply {
            text = "abc"; startOffset = 20; selectionStart = 1; selectionEnd = 1
            partialStartOffset = -1
        }
        var cleanups = 0
        val verifier = EditorSelectionCleanup(scope, { active }, { revision }, Mutex(), reads)
        init {
            whenever(connection.getExtractedText(any<ExtractedTextRequest>(), any())).thenAnswer {
                assertEquals(InputConnection.GET_TEXT_WITH_STYLES, it.getArgument<ExtractedTextRequest>(0).flags)
                extracted
            }
        }
        fun check(scope: TestScope, start: Int = 21, end: Int = start) {
            verifier.verify(start, end) { cleanups++ }
            scope.runCurrent()
        }
        fun reply(scope: TestScope) { reads.reply(); scope.runCurrent() }
    }

    @Test fun staleNotificationCannotClearNewCompositionAtItsNewCaret() = runTest {
        val e = Editor(this)
        e.check(this)
        e.extracted!!.selectionStart = 2; e.extracted!!.selectionEnd = 2
        e.reply(this)
        assertEquals(0, e.cleanups)
    }

    @Test fun composingSpanProtectsTextEvenWhenTheCaretMatchesAnOldNotification() = runTest {
        val e = Editor(this)
        e.extracted!!.text = SpannableString("abc").apply {
            setSpan(UnderlineSpan(), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or Spanned.SPAN_COMPOSING)
        }
        e.check(this); e.reply(this)
        assertEquals(0, e.cleanups)
    }

    @Test fun genuineExternalMoveStillClearsFinishedLocalCompositionAtAbsoluteOffsets() = runTest {
        val e = Editor(this)
        e.extracted!!.selectionEnd = 3
        e.check(this, 21, 23); e.reply(this)
        assertEquals(1, e.cleanups)
    }

    @Test fun newInputOrCompositionWriteDuringReadInvalidatesCleanup() = runTest {
        val e = Editor(this)
        e.check(this); e.revision++; e.reply(this)
        assertEquals(0, e.cleanups)
    }

    @Test fun newSelectionOrSessionCancelsTheOutstandingCleanup() = runTest {
        val e = Editor(this)
        e.check(this); e.verifier.cancel(); e.reply(this)
        assertEquals(0, e.cleanups)
    }

    @Test fun replacementInputConnectionCannotReceiveOldCleanup() = runTest {
        val e = Editor(this)
        e.check(this); e.active = mock(); e.reply(this)
        assertEquals(0, e.cleanups)
    }

    @Test fun refusedOrPartialExtractionDoesNotJustifyClearingInput() = runTest {
        for (partial in listOf(false, true)) {
            val e = Editor(this)
            if (partial) e.extracted!!.partialStartOffset = 1 else e.extracted = null
            e.check(this); e.reply(this)
            assertEquals(0, e.cleanups)
        }
    }

    @Test fun refusedExtractionStillHonorsAnExternalMoveWhenStyledCursorTextIsAvailable() = runTest {
        val e = Editor(this)
        e.extracted = null
        whenever(e.connection.getTextBeforeCursor(any(), any())).thenReturn("a")
        e.check(this); e.reply(this)
        assertEquals(1, e.cleanups)
    }

    @Test fun fallbackCursorReadStillProtectsNewComposingText() = runTest {
        for (partial in listOf(false, true)) {
            val e = Editor(this)
            if (partial) e.extracted!!.partialStartOffset = 1 else e.extracted = null
            whenever(e.connection.getTextAfterCursor(any(), any())).thenReturn(SpannableString("あ").apply {
                setSpan(UnderlineSpan(), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or Spanned.SPAN_COMPOSING)
            })
            e.check(this); e.reply(this)
            assertEquals(0, e.cleanups)
        }
    }
    @Test fun inputWaiterFollowsAReplacementSelectionCheck() = runTest {
        val e = Editor(this)
        e.check(this)
        var resumed = false
        launch { e.verifier.awaitPending(); resumed = true }
        runCurrent()
        assertEquals(false, resumed)
        e.check(this)
        e.reply(this) // Canceled old read cannot release the waiting input.
        assertEquals(false, resumed)
        e.reply(this)
        assertEquals(1, e.cleanups)
        assertEquals(true, resumed)
    }

    @Test fun inputCheckUsesActualCaretBeforeTheNativeCallbackArrives() = runTest {
        val e = Editor(this)
        var caret: Pair<Int, Int>? = null
        e.extracted!!.selectionStart = 0; e.extracted!!.selectionEnd = 0
        e.verifier.verifyBeforeInput(21, 21) { start, end -> caret = start to end }
        runCurrent(); e.reply(this)
        assertEquals(20 to 20, caret)
    }

    @Test fun inputCheckPreservesExistingComposition() = runTest {
        val e = Editor(this)
        e.extracted!!.text = SpannableString("abc").apply {
            setSpan(UnderlineSpan(), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or Spanned.SPAN_COMPOSING)
        }
        e.verifier.verifyBeforeInput(21, 21) { _, _ -> e.cleanups++ }
        runCurrent(); e.reply(this)
        assertEquals(0, e.cleanups)
    }

}
