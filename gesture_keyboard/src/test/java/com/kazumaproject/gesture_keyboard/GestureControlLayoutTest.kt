package com.kazumaproject.gesture_keyboard

import com.kazumaproject.custom_keyboard.data.KeyAction

import org.junit.Assert.*
import org.junit.Test

class GestureControlLayoutTest {
    private val a = GestureControlKey("a", KeyAction.Delete)
    private val b = GestureControlKey("b", KeyAction.Delete)
    private val c = GestureControlKey("c", KeyAction.Enter)
    @Test fun insertionWithinColumnPreservesIdentityAndOtherOrder() {
        val layout = GestureControlLayout(listOf(a, b, c), emptyList())
        assertEquals(listOf(b, c, a), layout.move("a", GestureSide.LEFT, 3).leftKeys)
        assertEquals(layout, layout.move("b", GestureSide.LEFT, 2))
    }
    @Test fun moveIntoEmptySideDoesNotRelabelTheOtherSide() {
        val moved = GestureControlLayout(listOf(a), listOf(b)).move("a", GestureSide.RIGHT, 0)
        assertEquals(emptyList<GestureControlKey>(), moved.leftKeys)
        assertEquals(listOf(a, b), moved.rightKeys)
        moved.validate()
        GestureControlLayout(emptyList(), emptyList()).validate()
    }
    @Test fun invalidDropIsNoOpAndDuplicateActionsRemainValid() {
        val layout = GestureControlLayout(listOf(a, b), listOf(c))
        layout.validate()
        assertEquals(layout, layout.move("missing", GestureSide.LEFT, 0))
        assertThrows(IllegalArgumentException::class.java) { GestureControlLayout(listOf(a), listOf(a)).validate() }
    }
}
