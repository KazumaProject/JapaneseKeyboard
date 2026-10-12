package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor

import android.view.KeyEvent
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class EditorCursorSelectionTest {
    private class Editor(val text: String = "a👨‍👩‍👧‍👦e\u0301z", val offset: Int = 50) {
        var start = offset
        var end = offset
        var partial = false
        var refused = false
        var active = true
        var notifyDuringRead = false
        var selectionRevision = 0L
        var readCount = 0
        val writes = mutableListOf<Pair<Int, Int>>()
        val events = mutableListOf<KeyEvent>()
        val connection: InputConnection = mock()
        val controller = EditorCursorSelection({ connection.takeIf { active } }, { 0L }, { selectionRevision }, Mutex()) { a, b ->
            writes += a to b; start = a; end = b; true
        }
        init {
            whenever(connection.getExtractedText(any<ExtractedTextRequest>(), any())).thenAnswer {
                readCount++
                if (notifyDuringRead) { notifyDuringRead = false; selectionRevision++ }
                if (refused) null else ExtractedText().apply {
                    this.text = this@Editor.text; startOffset = offset
                    selectionStart = start - offset; selectionEnd = end - offset
                    partialStartOffset = if (partial) 1 else -1
                }
            }
            whenever(connection.sendKeyEvent(any())).thenAnswer { events += it.getArgument<KeyEvent>(0); true }
        }
        fun move(direction: CursorDirection, selected: Boolean = true, current: () -> Boolean = { true }) = runBlocking {
            controller.move(direction, selected, current)
        }
    }

    @Test fun emojiAndCombiningSequenceMoveAsWholeGraphemesAtAbsoluteOffsets() {
        val e = Editor()
        e.move(CursorDirection.RIGHT)
        assertEquals(50 to 51, e.writes.last())
        e.move(CursorDirection.RIGHT)
        assertEquals(50 to 62, e.writes.last())
        e.move(CursorDirection.RIGHT)
        assertEquals(50 to 64, e.writes.last())
        e.move(CursorDirection.LEFT)
        assertEquals(50 to 62, e.writes.last())
        e.move(CursorDirection.LEFT)
        e.move(CursorDirection.LEFT)
        assertEquals(50 to 50, e.writes.last())
        e.move(CursorDirection.LEFT)
        assertEquals(50 to 50, e.writes.last())
    }

    @Test fun modifiersFlagsAndVariationSelectorsAreWholeUnitsOnOldAndNewAndroid() {
        val e = Editor("👍🏽🇯🇵✌️", 0)
        e.move(CursorDirection.RIGHT); assertEquals(0 to 4, e.writes.last())
        e.move(CursorDirection.RIGHT); assertEquals(0 to 8, e.writes.last())
        e.move(CursorDirection.RIGHT); assertEquals(0 to 10, e.writes.last())
        e.move(CursorDirection.LEFT); assertEquals(0 to 8, e.writes.last())
        e.move(CursorDirection.LEFT); assertEquals(0 to 4, e.writes.last())
        e.move(CursorDirection.LEFT); assertEquals(0 to 0, e.writes.last())
    }

    @Test fun lateSelectionCallbackRetriesReadWithoutDroppingACompletedStep() {
        val e = Editor("abcdef", 20)
        e.notifyDuringRead = true
        e.move(CursorDirection.RIGHT)
        assertEquals(2, e.readCount)
        assertEquals(20 to 21, e.writes.single())
    }

    @Test fun inputTargetChangingDuringReadNeverReceivesTheOldAction() {
        val e = Editor()
        whenever(e.connection.getExtractedText(any<ExtractedTextRequest>(), any())).thenAnswer {
            e.active = false
            ExtractedText().apply { text = "abc"; selectionStart = 0; selectionEnd = 0; partialStartOffset = -1 }
        }
        e.move(CursorDirection.RIGHT)
        assertTrue(e.writes.isEmpty()); assertTrue(e.events.isEmpty())
    }

    @Test fun reversedRangeRetainsAnchorAndSwitchesBetweenShrinkAndExpand() {
        val e = Editor("abcdef", 20)
        e.start = 23; e.end = 23
        e.move(CursorDirection.LEFT)
        assertEquals(23 to 22, e.writes.last())
        e.controller.onSelectionChanged(22, 23) // Editors may report sorted ranges.
        e.move(CursorDirection.RIGHT)
        assertEquals(23 to 23, e.writes.last())
        e.move(CursorDirection.RIGHT)
        assertEquals(23 to 24, e.writes.last())
    }

    @Test fun offPreservesRangeUntilFirstMovementAndCollapsesAtDirectionalEndpoint() {
        for ((direction, target) in listOf(CursorDirection.LEFT to 21, CursorDirection.RIGHT to 24)) {
            val e = Editor("abcdef", 20)
            e.start = 24; e.end = 21
            e.move(direction, false)
            assertEquals(target to target, e.writes.single())
            assertTrue(e.events.isEmpty())
        }
    }

    @Test fun verticalMovementUsesShiftAndOffCollapsesAtMovingEndBeforeLineMovement() {
        val e = Editor("abcdef", 20)
        e.start = 23; e.end = 23
        e.move(CursorDirection.LEFT)
        e.move(CursorDirection.UP)
        val down = e.events.first { it.keyCode == KeyEvent.KEYCODE_DPAD_UP && it.action == KeyEvent.ACTION_DOWN }
        assertTrue(down.isShiftPressed)
        assertEquals(KeyEvent.KEYCODE_SHIFT_LEFT, e.events.last().keyCode)
        assertEquals(KeyEvent.ACTION_UP, e.events.last().action)
        e.events.clear()
        e.move(CursorDirection.DOWN, false)
        assertEquals(22 to 22, e.writes.last())
        assertFalse(e.events.first().isShiftPressed)
    }

    @Test fun edgesOfAnExtractedWindowDelegateToTheEditorInsteadOfAssumingDocumentEdges() {
        for ((direction, position) in listOf(CursorDirection.LEFT to 50, CursorDirection.RIGHT to 53)) {
            val e = Editor("abc", 50)
            e.start = position; e.end = position
            e.move(direction)
            assertTrue(e.writes.isEmpty())
            assertTrue(e.events.first { it.keyCode == direction.keyCode }.isShiftPressed)
        }
    }

    @Test fun partialOrUnavailableTextFallsBackWithoutInventingAbsoluteSelections() {
        for (refused in listOf(false, true)) {
            val e = Editor()
            e.partial = !refused; e.refused = refused
            e.move(CursorDirection.RIGHT)
            assertTrue(e.writes.isEmpty())
            assertTrue(e.events.first { it.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT }.isShiftPressed)
        }
    }

    @Test fun canceledOrReplacedInputNeverReceivesOldMovement() {
        val e = Editor()
        e.move(CursorDirection.RIGHT, current = { false })
        e.active = false
        e.move(CursorDirection.LEFT)
        assertTrue(e.writes.isEmpty()); assertTrue(e.events.isEmpty())
    }

    @Test fun externalSelectionAndInputTargetResetReplaceOldAnchor() {
        val e = Editor("abcdef", 20)
        e.start = 23; e.end = 23; e.move(CursorDirection.LEFT)
        e.start = 25; e.end = 25; e.controller.onSelectionChanged(25, 25)
        e.move(CursorDirection.LEFT)
        assertEquals(25 to 24, e.writes.last())
        e.controller.reset(); e.start = 21; e.end = 21
        e.move(CursorDirection.RIGHT)
        assertEquals(21 to 22, e.writes.last())
    }
}
