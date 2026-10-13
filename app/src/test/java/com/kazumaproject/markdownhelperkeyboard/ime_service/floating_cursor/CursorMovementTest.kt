package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor

import android.view.KeyEvent
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
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
class CursorMovementTest {
    private class Editor(val text: String, var start: Int, var end: Int = start, val offset: Int = 0, dispatcher: CoroutineDispatcher) {
        val connection = mock<InputConnection>()
        val selections = mutableListOf<Pair<Int, Int>>()
        val keys = mutableListOf<KeyEvent>()
        var refuseExtraction = false
        val movement = CursorMovement(Mutex(), { _, a, b ->
            selections += a to b; start = a; end = b; true
        }, { _, key -> keys += key; true }, dispatcher)
        init {
            whenever(connection.getExtractedText(any(), eq(0))).thenAnswer {
                if (refuseExtraction) null else ExtractedText().apply {
                    text = this@Editor.text
                    startOffset = offset
                    selectionStart = start - offset
                    selectionEnd = end - offset
                    partialStartOffset = -1
                }
            }
        }
        suspend fun move(direction: CursorDirection, selecting: Boolean, current: () -> Boolean = { true }) =
            movement.move(connection, direction, selecting, current)
    }

    @Test fun selectionExtendsShrinksAndReversesAroundTheSameFixedEnd() = runTest {
        val editor = Editor("abcdef", 3, dispatcher = UnconfinedTestDispatcher(testScheduler))
        editor.move(CursorDirection.LEFT, true)
        editor.move(CursorDirection.LEFT, true)
        editor.move(CursorDirection.RIGHT, true)
        editor.move(CursorDirection.RIGHT, true)
        editor.move(CursorDirection.RIGHT, true)
        assertEquals(listOf(3 to 2, 3 to 1, 3 to 2, 3 to 3, 3 to 4), editor.selections)
        assertTrue(editor.keys.isEmpty())
    }

    @Test fun disablingSelectionCollapsesTheRangeBeforeMovingAgain() = runTest {
        val editor = Editor("abcdef", 4, dispatcher = UnconfinedTestDispatcher(testScheduler))
        editor.move(CursorDirection.LEFT, true)
        editor.move(CursorDirection.LEFT, false)
        assertEquals(3 to 3, editor.selections.last())
        editor.move(CursorDirection.RIGHT, false)
        assertEquals(4 to 4, editor.selections.last())
    }

    @Test fun reenablingWithoutMovingKeepsTheOriginalFixedEnd() = runTest {
        val editor = Editor("abcdef", 4, dispatcher = UnconfinedTestDispatcher(testScheduler))
        editor.move(CursorDirection.LEFT, true)
        // A toggle alone does not invoke reset or mutate native selection.
        editor.move(CursorDirection.RIGHT, true)
        assertEquals(4 to 4, editor.selections.last())
    }

    @Test fun unicodeClustersAreNotSplitInEitherDirection() = runTest {
        for (cluster in listOf("😀", "e\u0301", "👩‍👩‍👧‍👦", "🇯🇵", "👍🏽", "✌️")) {
            val editor = Editor("a${cluster}b", cluster.length + 1, dispatcher = UnconfinedTestDispatcher(testScheduler))
            editor.move(CursorDirection.LEFT, false)
            assertEquals("Left split $cluster", 1 to 1, editor.selections.last())
            editor.move(CursorDirection.RIGHT, false)
            assertEquals("Right split $cluster", cluster.length + 1 to cluster.length + 1, editor.selections.last())
        }
    }

    @Test fun extractionOffsetsAreAppliedToAbsoluteSelection() = runTest {
        val editor = Editor("abcdef", 103, offset = 100, dispatcher = UnconfinedTestDispatcher(testScheduler))
        editor.move(CursorDirection.LEFT, true)
        assertEquals(103 to 102, editor.selections.single())
    }

    @Test fun refusedExtractionAndVerticalMovementUseNativeShiftDpad() = runTest {
        val editor = Editor("abcdef", 3, dispatcher = UnconfinedTestDispatcher(testScheduler))
        editor.refuseExtraction = true
        editor.move(CursorDirection.LEFT, true)
        assertEquals(listOf(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_SHIFT_LEFT), editor.keys.map { it.keyCode })
        val arrows = editor.keys.filter { it.keyCode == KeyEvent.KEYCODE_DPAD_LEFT }
        assertEquals(listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP), arrows.map { it.action })
        assertTrue(arrows.all { it.isShiftPressed && it.flags and KeyEvent.FLAG_SOFT_KEYBOARD != 0 })
        assertEquals(KeyEvent.ACTION_UP, editor.keys.last().action)
        assertFalse(editor.keys.last().isShiftPressed)
        editor.keys.clear()
        editor.refuseExtraction = false
        editor.move(CursorDirection.DOWN, true)
        assertEquals(4, editor.keys.size)
        assertTrue(editor.keys.filter { it.keyCode == KeyEvent.KEYCODE_DPAD_DOWN }.all { it.isShiftPressed })
        assertEquals(KeyEvent.KEYCODE_SHIFT_LEFT, editor.keys.last().keyCode)
        assertEquals(KeyEvent.ACTION_UP, editor.keys.last().action)
    }

    @Test fun aStaleOperationNeitherChangesSelectionNorSendsDpad() = runTest {
        val editor = Editor("abcdef", 3, dispatcher = UnconfinedTestDispatcher(testScheduler))
        editor.move(CursorDirection.LEFT, true) { false }
        assertTrue(editor.selections.isEmpty())
        assertTrue(editor.keys.isEmpty())
    }

    @Test fun anExternalCaretChangeDuringTheReadDoesNotApplyTheOlderAbsolutePosition() = runTest {
        val editor = mock<InputConnection>()
        var caret = 2
        var selectionRevision = 0L
        val selections = mutableListOf<Pair<Int, Int>>()
        val keys = mutableListOf<KeyEvent>()
        whenever(editor.getExtractedText(any(), eq(0))).thenAnswer {
            ExtractedText().apply {
                text = "abcdef"; startOffset = 0; partialStartOffset = -1
                selectionStart = caret; selectionEnd = caret
            }.also { caret = 5; selectionRevision++ }
        }
        val movement = CursorMovement(Mutex(), { _, start, end ->
            selections += start to end; caret = end; true
        }, { _, key -> keys += key; true }, StandardTestDispatcher(testScheduler),
            { selectionRevision }, { caret to caret })
        movement.move(editor, CursorDirection.LEFT, false) { true }
        assertEquals(5, caret)
        assertTrue(selections.isEmpty())
        assertTrue(keys.isEmpty())
    }

    @Test fun delayedSelectionAcknowledgementsDoNotDropConsecutiveSelectionSteps() = runTest {
        val editor = mock<InputConnection>()
        var start = 5
        var end = 5
        var selectionRevision = 0L
        val selections = mutableListOf<Pair<Int, Int>>()
        whenever(editor.getExtractedText(any(), eq(0))).thenAnswer {
            ExtractedText().apply {
                text = "abcdef"; startOffset = 0; partialStartOffset = -1
                selectionStart = start; selectionEnd = end
            }.also { selectionRevision++ } // The preceding move's matching notification arrives during this read.
        }
        val movement = CursorMovement(Mutex(), { _, a, b ->
            selections += a to b; start = a; end = b; true
        }, { _, _ -> fail("A valid horizontal step must not fall back to DPAD"); false },
            StandardTestDispatcher(testScheduler), { selectionRevision }, { start to end })
        repeat(3) { movement.move(editor, CursorDirection.LEFT, true) { true } }
        assertEquals(listOf(5 to 4, 5 to 3, 5 to 2), selections)
    }

    @Test fun anExternalSelectionChangeAlsoCancelsUnreadableNativeFallback() = runTest {
        val editor = mock<InputConnection>()
        var revision = 0L
        val keys = mutableListOf<KeyEvent>()
        whenever(editor.getExtractedText(any(), eq(0))).thenAnswer { revision++; null }
        val movement = CursorMovement(Mutex(), { _, _, _ -> fail("Must not change the external selection"); false },
            { _, key -> keys += key; true }, StandardTestDispatcher(testScheduler), { revision }, { 5 to 5 })
        movement.move(editor, CursorDirection.UP, true) { true }
        assertTrue(keys.isEmpty())
    }

    @Test fun aWindowedTextBoundaryFallsBackToTheEditor() = runTest {
        val editor = Editor("abcdef", 100, offset = 100, dispatcher = UnconfinedTestDispatcher(testScheduler))
        editor.move(CursorDirection.LEFT, false)
        assertTrue(editor.selections.isEmpty())
        assertEquals(2, editor.keys.size)
        assertFalse(editor.keys.first().isShiftPressed)
    }

    @Test fun trackpadAccumulatesSmallDeltasAndResetsBetweenGestures() {
        val moves = mutableListOf<CursorDirection>()
        val pad = CursorPadGesture(20f) { moves += it }
        pad.begin(0f, 0f)
        pad.move(12f, 0f)
        assertTrue(moves.isEmpty())
        pad.move(21f, 0f)
        assertEquals(listOf(CursorDirection.RIGHT), moves)
        pad.move(21f, -40f)
        assertEquals(listOf(CursorDirection.RIGHT, CursorDirection.UP, CursorDirection.UP), moves)
        pad.begin(0f, 0f)
        pad.move(19f, 0f)
        assertEquals(3, moves.size)
    }
}
