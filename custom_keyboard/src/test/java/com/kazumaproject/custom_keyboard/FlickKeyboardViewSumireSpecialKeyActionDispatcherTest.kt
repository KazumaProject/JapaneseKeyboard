package com.kazumaproject.custom_keyboard

import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.ResolvedSumireSpecialKeyAction
import com.kazumaproject.custom_keyboard.data.SumireSpecialKeyDirection
import com.kazumaproject.custom_keyboard.data.dispatchResolvedSumireSpecialKeyAction
import com.kazumaproject.custom_keyboard.data.toSumireSpecialKeyDirectionOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlickKeyboardViewSumireSpecialKeyActionDispatcherTest {
    @Test
    fun tapOverrideDispatchesActionWithNonFlickFlag() {
        assertDispatch(
            flickDirection = FlickDirection.TAP,
            expectedSumireDirection = SumireSpecialKeyDirection.TAP,
            resolved = ResolvedSumireSpecialKeyAction.Action(KeyAction.Paste),
            isFlick = false,
            expected = listOf(KeyAction.Paste to false)
        )
    }

    @Test
    fun upOverrideDispatchesActionWithFlickFlag() {
        assertDispatch(
            flickDirection = FlickDirection.UP,
            expectedSumireDirection = SumireSpecialKeyDirection.UP,
            resolved = ResolvedSumireSpecialKeyAction.Action(KeyAction.Delete),
            isFlick = true,
            expected = listOf(KeyAction.Delete to true)
        )
    }

    @Test
    fun rightOverrideUsesUpRightFarAndDispatchesActionWithFlickFlag() {
        assertDispatch(
            flickDirection = FlickDirection.UP_RIGHT_FAR,
            expectedSumireDirection = SumireSpecialKeyDirection.RIGHT,
            resolved = ResolvedSumireSpecialKeyAction.Action(KeyAction.Enter),
            isFlick = true,
            expected = listOf(KeyAction.Enter to true)
        )
    }

    @Test
    fun downOverrideDispatchesActionWithFlickFlag() {
        assertDispatch(
            flickDirection = FlickDirection.DOWN,
            expectedSumireDirection = SumireSpecialKeyDirection.DOWN,
            resolved = ResolvedSumireSpecialKeyAction.Action(KeyAction.Space),
            isFlick = true,
            expected = listOf(KeyAction.Space to true)
        )
    }

    @Test
    fun leftOverrideUsesUpLeftFarAndDispatchesActionWithFlickFlag() {
        assertDispatch(
            flickDirection = FlickDirection.UP_LEFT_FAR,
            expectedSumireDirection = SumireSpecialKeyDirection.LEFT,
            resolved = ResolvedSumireSpecialKeyAction.Action(KeyAction.Copy),
            isFlick = true,
            expected = listOf(KeyAction.Copy to true)
        )
    }

    @Test
    fun defaultFallsBackToOriginalAction() {
        val dispatched = mutableListOf<Pair<KeyAction, Boolean>>()
        val handled = dispatchResolvedSumireSpecialKeyAction(
            ResolvedSumireSpecialKeyAction.Default,
            isFlick = true
        ) { action, isFlick -> dispatched += action to isFlick }

        assertFalse(handled)
        assertTrue(dispatched.isEmpty())
    }

    @Test
    fun noneConsumesActionWithoutDispatchingOriginalAction() {
        val dispatched = mutableListOf<Pair<KeyAction, Boolean>>()
        val handled = dispatchResolvedSumireSpecialKeyAction(
            ResolvedSumireSpecialKeyAction.None,
            isFlick = true
        ) { action, isFlick -> dispatched += action to isFlick }

        assertTrue(handled)
        assertTrue(dispatched.isEmpty())
    }

    @Test
    fun inputTextDispatchesTextAction() {
        val dispatched = mutableListOf<Pair<KeyAction, Boolean>>()
        val handled = dispatchResolvedSumireSpecialKeyAction(
            ResolvedSumireSpecialKeyAction.InputText("abc"),
            isFlick = true
        ) { action, isFlick -> dispatched += action to isFlick }

        assertTrue(handled)
        assertEquals(listOf(KeyAction.Text("abc") to true), dispatched)
    }

    @Test
    fun onPressPathDoesNotUseActionDispatcher() {
        val dispatched = mutableListOf<Pair<KeyAction, Boolean>>()

        assertTrue(dispatched.isEmpty())
    }

    private fun assertDispatch(
        flickDirection: FlickDirection,
        expectedSumireDirection: SumireSpecialKeyDirection,
        resolved: ResolvedSumireSpecialKeyAction,
        isFlick: Boolean,
        expected: List<Pair<KeyAction, Boolean>>
    ) {
        assertEquals(expectedSumireDirection, flickDirection.toSumireSpecialKeyDirectionOrNull())
        val dispatched = mutableListOf<Pair<KeyAction, Boolean>>()
        val handled = dispatchResolvedSumireSpecialKeyAction(resolved, isFlick) { action, actionIsFlick ->
            dispatched += action to actionIsFlick
        }

        assertTrue(handled)
        assertEquals(expected, dispatched)
    }
}
