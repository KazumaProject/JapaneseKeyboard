package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.coroutines.CoroutineContext

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class ForwardDeleteCoordinatorTest {
    // Hold the InputConnection reply until the test explicitly delivers it. Main-thread
    // selection changes and additional key taps can then run while that read is outstanding.
    private class Reads : CoroutineDispatcher() {
        val queued = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { queued.add(block) }
        fun reply() { queued.removeFirst().run() }
    }

    private class Editor(scope: TestScope) {
        val reads = Reads()
        var text = "abcd"
        var start = 0
        var end = 0
        var revision = 0L
        var editable = true
        var acceptsDelete = true
        var exposesText = true
        var deleteWidth = 1
        var delayDelete = false
        var pendingDelete: (() -> Unit)? = null
        var events = 0
        val history = mutableListOf<String>()
        val connection = mock<InputConnection>()
        var active: InputConnection? = connection
        val coordinator: ForwardDeleteCoordinator = ForwardDeleteCoordinator(
            scope, { active }, { revision }, { editable },
            delete = {
                events++
                revision++
                val edit = {
                    val from = minOf(start, end)
                    val to = if (start != end) maxOf(start, end) else (from + deleteWidth).coerceAtMost(text.length)
                    text = text.removeRange(from, to)
                    start = from
                    end = from
                    coordinatorSelectionChanged()
                }
                if (acceptsDelete) {
                    if (delayDelete) pendingDelete = edit else edit()
                }
            },
            recordDeletion = { history.add(it) },
            readDispatcher = reads,
        )
        init {
            whenever(connection.getExtractedText(any(), any())).doAnswer {
                if (!exposesText) null else ExtractedText().also {
                    it.text = text
                    it.startOffset = 0
                    it.partialStartOffset = -1
                    it.partialEndOffset = -1
                    it.selectionStart = start
                    it.selectionEnd = end
                }
            }
            coordinator.reset(0, 0)
        }
        private fun coordinatorSelectionChanged() { coordinator.onSelectionChanged(start, end) }
        fun move(from: Int, to: Int = from) {
            start = from
            end = to
            coordinatorSelectionChanged()
        }
        fun drain(scope: TestScope) {
            scope.advanceUntilIdle()
            while (reads.queued.isNotEmpty()) {
                reads.reply()
                scope.advanceUntilIdle()
            }
        }
    }

    @Test fun rapidTapsDuringReadDeleteEveryCharacterInOrder() = runTest {
        val editor = Editor(this)
        editor.coordinator.enqueue()
        runCurrent()
        editor.coordinator.enqueue()
        editor.coordinator.enqueue()
        editor.drain(this)
        assertEquals("d", editor.text)
        assertEquals(3, editor.events)
        assertEquals(listOf("a", "b", "c"), editor.history)
    }

    @Test fun waitsForPostedKeyEventBeforeRecordingHistoryOrSendingNextKey() = runTest {
        val editor = Editor(this)
        editor.delayDelete = true
        editor.coordinator.enqueue()
        editor.coordinator.enqueue()
        runCurrent()
        editor.reads.reply() // Before snapshot.
        runCurrent() // The editor queues the key instead of applying it synchronously.
        editor.reads.reply() // Immediate post-key read still contains the old text.
        runCurrent()
        assertEquals(1, editor.events)
        assertEquals(emptyList<String>(), editor.history)
        editor.pendingDelete!!.invoke()
        editor.delayDelete = false
        editor.drain(this)
        assertEquals("cd", editor.text)
        assertEquals(listOf("a", "b"), editor.history)
    }

    @Test fun cursorMoveAfterReadBeforeDispatchCancelsDeletionAndHistory() = runTest {
        val editor = Editor(this)
        editor.coordinator.enqueue()
        runCurrent()
        editor.reads.reply()
        editor.move(2)
        editor.drain(this)
        assertEquals("abcd", editor.text)
        assertEquals(0, editor.events)
        assertEquals(emptyList<String>(), editor.history)
    }

    @Test fun changingSelectedRangeWhileReadIsPendingCancelsWholeQueue() = runTest {
        val editor = Editor(this)
        editor.move(0, 2)
        editor.coordinator.enqueue()
        editor.coordinator.enqueue()
        runCurrent()
        editor.move(2, 4)
        editor.drain(this)
        assertEquals("abcd", editor.text)
        assertEquals(0, editor.events)
    }

    @Test fun selectionDeletionThenQueuedTapUsesAcknowledgedCollapsedCursor() = runTest {
        val editor = Editor(this)
        editor.move(2, 0)
        editor.coordinator.enqueue()
        editor.coordinator.enqueue()
        editor.drain(this)
        assertEquals("d", editor.text)
        assertEquals(listOf("ab", "c"), editor.history)
    }

    @Test fun redoAfterSnapshotCancelsUnsentDeleteAndDoesNotDuplicateHistory() = runTest {
        val editor = Editor(this)
        editor.coordinator.enqueue()
        editor.coordinator.enqueue()
        runCurrent()
        editor.reads.reply()
        // Redo deletes at the same caret while the pre-delete reply awaits Main.
        editor.coordinator.cancel()
        editor.text = "bcd"
        editor.revision++
        editor.coordinator.onSelectionChanged(0, 0)
        editor.drain(this)
        assertEquals("bcd", editor.text)
        assertEquals(0, editor.events)
        assertEquals(emptyList<String>(), editor.history)
        editor.coordinator.enqueue()
        editor.drain(this)
        assertEquals("cd", editor.text)
        assertEquals(listOf("b"), editor.history)
    }

    @Test fun historyEditDuringAcknowledgementDropsOldHistoryAndRemainingTaps() = runTest {
        for (replacement in listOf("abcd", "cd")) {
            val editor = Editor(this)
            editor.coordinator.enqueue()
            editor.coordinator.enqueue()
            runCurrent()
            editor.reads.reply()
            runCurrent() // First key applied; its acknowledgement is still pending.
            editor.coordinator.cancel()
            editor.text = replacement // Undo or Redo at the same caret.
            editor.revision++
            editor.drain(this)
            assertEquals(replacement, editor.text)
            assertEquals(1, editor.events)
            assertEquals(emptyList<String>(), editor.history)
        }
    }

    @Test fun revisionChangeAfterSnapshotRejectsStaleReplyAtSameCaret() = runTest {
        val editor = Editor(this)
        editor.coordinator.enqueue()
        runCurrent()
        editor.reads.reply()
        editor.text = "bcd"
        editor.revision++
        editor.drain(this)
        assertEquals(0, editor.events)
        assertEquals(emptyList<String>(), editor.history)
    }

    @Test fun externalEditInvalidatesQueuedTaps() = runTest {
        val editor = Editor(this)
        editor.coordinator.enqueue()
        runCurrent()
        editor.revision++
        editor.drain(this)
        assertEquals(0, editor.events)
    }

    @Test fun restartedSessionUsingSameConnectionRejectsLateReply() = runTest {
        val editor = Editor(this)
        editor.coordinator.enqueue()
        runCurrent()
        editor.coordinator.reset(0, 0)
        editor.coordinator.enqueue()
        editor.drain(this)
        assertEquals("bcd", editor.text)
        assertEquals(listOf("a"), editor.history)
    }

    @Test fun connectionSwitchRejectsLateReply() = runTest {
        val editor = Editor(this)
        editor.coordinator.enqueue()
        runCurrent()
        editor.active = mock()
        editor.drain(this)
        assertEquals(0, editor.events)
    }

    @Test fun cursorMoveAwayAndBackStillCancelsRequest() = runTest {
        val editor = Editor(this)
        editor.coordinator.enqueue()
        runCurrent()
        editor.move(2)
        editor.move(0)
        editor.drain(this)
        assertEquals(0, editor.events)
    }

    @Test fun editorIgnoringKeyDoesNotCreateUndoEntry() = runTest {
        val editor = Editor(this)
        editor.acceptsDelete = false
        editor.coordinator.enqueue()
        editor.drain(this)
        assertEquals("abcd", editor.text)
        assertEquals(emptyList<String>(), editor.history)
    }

    @Test fun editorWithoutExtractedTextStillReceivesKeysWithoutInventedHistory() = runTest {
        val editor = Editor(this)
        editor.exposesText = false
        editor.coordinator.enqueue()
        editor.coordinator.enqueue()
        editor.drain(this)
        assertEquals("cd", editor.text)
        assertEquals(2, editor.events)
        assertEquals(emptyList<String>(), editor.history)
    }

    @Test fun unknownInitialSelectionUsesEditorSnapshot() = runTest {
        val editor = Editor(this)
        editor.start = 2
        editor.end = 2
        editor.coordinator.reset(-1, -1)
        editor.coordinator.enqueue()
        editor.drain(this)
        assertEquals("abd", editor.text)
        assertEquals(listOf("c"), editor.history)
    }

    @Test fun historyRecordsActualEditorDeletionRatherThanPredictedGrapheme() = runTest {
        val editor = Editor(this)
        editor.deleteWidth = 2
        editor.coordinator.enqueue()
        editor.drain(this)
        assertEquals("cd", editor.text)
        assertEquals(listOf("ab"), editor.history)
    }

    @Test fun moveAfterDispatchDoesNotAttachUndoToNewCursorOrRunQueuedKey() = runTest {
        val editor = Editor(this)
        editor.coordinator.enqueue()
        editor.coordinator.enqueue()
        runCurrent()
        editor.reads.reply()
        runCurrent()
        editor.move(2)
        editor.drain(this)
        assertEquals("bcd", editor.text)
        assertEquals(1, editor.events)
        assertEquals(emptyList<String>(), editor.history)
    }
}
