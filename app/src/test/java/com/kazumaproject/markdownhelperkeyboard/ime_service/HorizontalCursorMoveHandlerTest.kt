package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.sync.Mutex
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
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class HorizontalCursorMoveHandlerTest {
    private class QueuedDispatcher : CoroutineDispatcher() {
        val queued = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { queued.add(block) }
        fun reply() { queued.removeFirst().run() }
    }

    private class Editor(
        scope: TestScope,
        start: Int = 2,
        end: Int = start,
        offset: Int = 0,
    ) {
        val connection = mock<InputConnection>()
        val reads = QueuedDispatcher()
        var current: InputConnection? = connection
        var text = "abcd"
        var offset = offset
        var start = start
        var end = end
        var revision = 0L
        var selectionRevision = 0L
        var canCollapseSelection = true
        var extractionAvailable = true
        var acceptsSelection = true
        var fallbackSelectedText: String? = null
        var beforeCursorReads = 0
        var afterCursorReads = 0
        var extractions = 0
        val selectionWrites = mutableListOf<Pair<Int, Int>>()
        val dpadEvents = mutableListOf<HorizontalCursorMoveHandler.Direction>()

        val handler = HorizontalCursorMoveHandler(
            scope = scope,
            currentConnection = { current },
            currentRevision = { revision },
            currentSelectionRevision = { selectionRevision },
            canCollapseSelection = { canCollapseSelection },
            readMutex = Mutex(),
            setSelection = { connection, newStart, newEnd ->
                connection.setSelection(newStart, newEnd)
            },
            sendDpad = { dpadEvents.add(it) },
            readDispatcher = reads,
            mainDispatcher = UnconfinedTestDispatcher(scope.testScheduler),
        )

        init {
            whenever(connection.getExtractedText(any<ExtractedTextRequest>(), any())).thenAnswer {
                extractions++
                if (!extractionAvailable) null else ExtractedText().apply {
                    text = this@Editor.text
                    startOffset = this@Editor.offset
                    partialStartOffset = -1
                    partialEndOffset = -1
                    selectionStart = this@Editor.start
                    selectionEnd = this@Editor.end
                }
            }
            whenever(connection.getSelectedText(any())).thenAnswer {
                val from = this@Editor.start
                val to = this@Editor.end
                fallbackSelectedText ?: if (from != to) {
                    text.substring(minOf(from, to), maxOf(from, to))
                } else null
            }
            whenever(connection.getTextBeforeCursor(any(), any())).thenAnswer {
                beforeCursorReads++
                if (this@Editor.start != this@Editor.end) null else {
                    text.substring(0, this@Editor.end).takeLast(it.getArgument<Int>(0))
                }
            }
            whenever(connection.getTextAfterCursor(any(), any())).thenAnswer {
                afterCursorReads++
                if (this@Editor.start != this@Editor.end) null else {
                    text.substring(this@Editor.start).take(it.getArgument<Int>(0))
                }
            }
            whenever(connection.setSelection(any(), any())).thenAnswer {
                val newStart = it.getArgument<Int>(0)
                val newEnd = it.getArgument<Int>(1)
                selectionWrites.add(newStart to newEnd)
                if (!acceptsSelection) false else {
                    this@Editor.start = newStart - this@Editor.offset
                    this@Editor.end = newEnd - this@Editor.offset
                    true
                }
            }
        }

        fun dispatch(scope: TestScope, direction: HorizontalCursorMoveHandler.Direction) {
            handler.move(direction)
            scope.runCurrent()
            while (reads.queued.isNotEmpty()) {
                reads.reply()
                scope.runCurrent()
            }
            scope.runCurrent()
        }
    }

    @Test fun leftCollapsesFullAndPartialSelectionsAtTheirLowerAbsoluteEndpoint() = runTest {
        val full = Editor(this, start = 0, end = 4, offset = 50)
        full.dispatch(this, HorizontalCursorMoveHandler.Direction.Left)
        assertEquals(listOf(50 to 50), full.selectionWrites)
        assertEquals(emptyList<HorizontalCursorMoveHandler.Direction>(), full.dpadEvents)
        assertEquals(1, full.extractions)

        for ((start, end) in listOf(1 to 3, 3 to 1)) {
            val partial = Editor(this, start = start, end = end, offset = 20)
            partial.dispatch(this, HorizontalCursorMoveHandler.Direction.Left)
            assertEquals(listOf(21 to 21), partial.selectionWrites)
            assertEquals(emptyList<HorizontalCursorMoveHandler.Direction>(), partial.dpadEvents)
        }
    }

    @Test fun rightCollapsesFullAndPartialSelectionsAtTheirUpperAbsoluteEndpoint() = runTest {
        val full = Editor(this, start = 0, end = 4, offset = 50)
        full.dispatch(this, HorizontalCursorMoveHandler.Direction.Right)
        assertEquals(listOf(54 to 54), full.selectionWrites)
        assertEquals(emptyList<HorizontalCursorMoveHandler.Direction>(), full.dpadEvents)

        for ((start, end) in listOf(1 to 3, 3 to 1)) {
            val partial = Editor(this, start = start, end = end, offset = 20)
            partial.dispatch(this, HorizontalCursorMoveHandler.Direction.Right)
            assertEquals(listOf(23 to 23), partial.selectionWrites)
            assertEquals(emptyList<HorizontalCursorMoveHandler.Direction>(), partial.dpadEvents)
        }
    }

    @Test fun collapsedCaretUsesDpadExceptAtTheMatchingDocumentBoundary() = runTest {
        val middleLeft = Editor(this, start = 2)
        middleLeft.dispatch(this, HorizontalCursorMoveHandler.Direction.Left)
        assertEquals(listOf(HorizontalCursorMoveHandler.Direction.Left), middleLeft.dpadEvents)

        val beginning = Editor(this, start = 0)
        beginning.dispatch(this, HorizontalCursorMoveHandler.Direction.Left)
        assertEquals(emptyList<HorizontalCursorMoveHandler.Direction>(), beginning.dpadEvents)

        val end = Editor(this, start = 4)
        end.dispatch(this, HorizontalCursorMoveHandler.Direction.Right)
        assertEquals(emptyList<HorizontalCursorMoveHandler.Direction>(), end.dpadEvents)

        val middleRight = Editor(this, start = 2)
        middleRight.dispatch(this, HorizontalCursorMoveHandler.Direction.Right)
        assertEquals(listOf(HorizontalCursorMoveHandler.Direction.Right), middleRight.dpadEvents)
    }

    @Test fun declinedExtractionUsesAdjacentTextAndStillCollapsesReportedSelection() = runTest {
        val caret = Editor(this, start = 2).apply { extractionAvailable = false }
        caret.dispatch(this, HorizontalCursorMoveHandler.Direction.Left)
        assertEquals(1, caret.extractions)
        assertEquals(1, caret.beforeCursorReads)
        assertEquals(listOf(HorizontalCursorMoveHandler.Direction.Left), caret.dpadEvents)

        val selected = Editor(this, start = 0, end = 4).apply { extractionAvailable = false }
        selected.dispatch(this, HorizontalCursorMoveHandler.Direction.Left)
        assertEquals(1, selected.extractions)
        assertEquals(emptyList<Pair<Int, Int>>(), selected.selectionWrites)
        assertEquals(listOf(HorizontalCursorMoveHandler.Direction.Left), selected.dpadEvents)
    }

    @Test fun refusedSelectionCollapseFallsBackToOneDpadAction() = runTest {
        val editor = Editor(this, start = 1, end = 3).apply { acceptsSelection = false }
        editor.dispatch(this, HorizontalCursorMoveHandler.Direction.Right)
        assertEquals(listOf(3 to 3), editor.selectionWrites)
        assertEquals(listOf(HorizontalCursorMoveHandler.Direction.Right), editor.dpadEvents)
    }

    @Test fun connectionReplacementDuringReadPreventsSelectionAndKeyDispatch() = runTest {
        val editor = Editor(this, start = 1, end = 3)
        editor.handler.move(HorizontalCursorMoveHandler.Direction.Left)
        runCurrent()
        assertEquals(1, editor.reads.queued.size)

        editor.current = mock()
        editor.reads.reply()
        runCurrent()
        advanceUntilIdle()

        assertEquals(emptyList<Pair<Int, Int>>(), editor.selectionWrites)
        assertEquals(emptyList<HorizontalCursorMoveHandler.Direction>(), editor.dpadEvents)
    }

    @Test fun selectionChangeOrEditorMutationDuringReadInvalidatesAbsoluteCollapse() = runTest {
        val selectionChanged = Editor(this, start = 1, end = 3)
        selectionChanged.handler.move(HorizontalCursorMoveHandler.Direction.Left)
        runCurrent()
        selectionChanged.selectionRevision++
        selectionChanged.start = 0
        selectionChanged.end = 2
        selectionChanged.reads.reply()
        runCurrent()
        assertEquals(emptyList<Pair<Int, Int>>(), selectionChanged.selectionWrites)

        val editorMutated = Editor(this, start = 1, end = 3)
        editorMutated.handler.move(HorizontalCursorMoveHandler.Direction.Right)
        runCurrent()
        editorMutated.revision++
        editorMutated.reads.reply()
        runCurrent()
        assertEquals(emptyList<Pair<Int, Int>>(), editorMutated.selectionWrites)
    }

    @Test fun enablingSelectModeDuringReadPreventsAbsoluteCollapse() = runTest {
        val editor = Editor(this, start = 1, end = 3)
        editor.handler.move(HorizontalCursorMoveHandler.Direction.Left)
        runCurrent()
        editor.canCollapseSelection = false
        editor.reads.reply()
        runCurrent()
        assertEquals(emptyList<Pair<Int, Int>>(), editor.selectionWrites)
        assertEquals(emptyList<HorizontalCursorMoveHandler.Direction>(), editor.dpadEvents)
    }

    @Test fun enablingSelectModeDuringFallbackSelectionReadPreventsDpadCollapse() = runTest {
        val editor = Editor(this, start = 0, end = 4).apply { extractionAvailable = false }
        editor.handler.move(HorizontalCursorMoveHandler.Direction.Left)
        runCurrent()
        editor.canCollapseSelection = false
        editor.reads.reply()
        runCurrent()
        assertEquals(emptyList<Pair<Int, Int>>(), editor.selectionWrites)
        assertEquals(emptyList<HorizontalCursorMoveHandler.Direction>(), editor.dpadEvents)
    }
}
