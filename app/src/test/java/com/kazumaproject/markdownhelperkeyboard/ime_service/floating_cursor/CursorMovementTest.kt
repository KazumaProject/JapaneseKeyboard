package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor

import android.view.KeyEvent
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
