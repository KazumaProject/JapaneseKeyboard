package com.kazumaproject.custom_keyboard

import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.ResolvedSumireSpecialKeyAction
import com.kazumaproject.custom_keyboard.data.SumireSpecialKeyDirection
import com.kazumaproject.custom_keyboard.data.dispatchSumireSpecialKeyRuntimeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SumireSpecialKeyRuntimeActionDispatcherTest {
    @Test
    fun tapOverrideDispatchesResolvedActionAndDoesNotDispatchFallback() {
        val result = dispatch(
            flickDirection = FlickDirection.TAP,
            fallbackAction = KeyAction.Delete,
            resolved = ResolvedSumireSpecialKeyAction.Action(KeyAction.Paste)
        )

        assertEquals(SumireSpecialKeyDirection.TAP, result.result.sumireDirection)
        assertTrue(result.result.handled)
        assertEquals(listOf(KeyAction.Paste to false), result.dispatched)
    }

    @Test
    fun tapDefaultDispatchesFallbackAction() {
        val result = dispatch(
            flickDirection = FlickDirection.TAP,
            fallbackAction = KeyAction.Delete,
            resolved = ResolvedSumireSpecialKeyAction.Default
        )

        assertFalse(result.result.handled)
        assertEquals(listOf(KeyAction.Delete to false), result.dispatched)
    }

    @Test
    fun noneConsumesWithoutDispatchingFallback() {
        val result = dispatch(
            flickDirection = FlickDirection.TAP,
            fallbackAction = KeyAction.Delete,
            resolved = ResolvedSumireSpecialKeyAction.None
        )

        assertTrue(result.result.handled)
        assertEquals(emptyList<Pair<KeyAction, Boolean>>(), result.dispatched)
    }

    @Test
    fun inputTextDispatchesTextActionWithoutDispatchingFallback() {
        val result = dispatch(
            flickDirection = FlickDirection.TAP,
            fallbackAction = KeyAction.Delete,
            resolved = ResolvedSumireSpecialKeyAction.InputText("abc")
        )

        assertTrue(result.result.handled)
        assertEquals(listOf(KeyAction.Text("abc") to false), result.dispatched)
    }

    @Test
    fun upRightDownLeftOverridesDispatchWithFlickFlagAndMappedDirections() {
        val cases = listOf(
            FlickDirection.UP to SumireSpecialKeyDirection.UP,
            FlickDirection.UP_RIGHT_FAR to SumireSpecialKeyDirection.RIGHT,
            FlickDirection.DOWN to SumireSpecialKeyDirection.DOWN,
            FlickDirection.UP_LEFT_FAR to SumireSpecialKeyDirection.LEFT
        )

        cases.forEach { (flickDirection, expectedDirection) ->
            val result = dispatch(
                flickDirection = flickDirection,
                fallbackAction = KeyAction.Delete,
                resolved = ResolvedSumireSpecialKeyAction.Action(KeyAction.Enter)
            )

            assertEquals(expectedDirection, result.result.sumireDirection)
            assertEquals(listOf(KeyAction.Enter to true), result.dispatched)
        }
    }

    @Test
    fun nonSpecialKeyDoesNotUseResolverAndDispatchesNormalFallback() {
        var resolverCalled = false
        val dispatched = mutableListOf<Pair<KeyAction, Boolean>>()
        val normalKey = specialKey.copy(isSpecialKey = false)

        dispatchSumireSpecialKeyRuntimeAction(
            keyData = normalKey,
            flickDirection = FlickDirection.TAP,
            fallbackAction = KeyAction.Space,
            isFlick = false,
            resolve = { _, _ ->
                resolverCalled = true
                ResolvedSumireSpecialKeyAction.Action(KeyAction.Paste)
            }
        ) { action, isFlick ->
            dispatched += action to isFlick
        }

        assertFalse(resolverCalled)
        assertEquals(listOf(KeyAction.Space to false), dispatched)
    }

    @Test
    fun overrideCanBeDispatchedWithoutDisplayOrFallbackAction() {
        val result = dispatch(
            flickDirection = FlickDirection.UP,
            fallbackAction = null,
            resolved = ResolvedSumireSpecialKeyAction.Action(KeyAction.Copy)
        )

        assertTrue(result.result.handled)
        assertEquals(listOf(KeyAction.Copy to true), result.dispatched)
    }

    private fun dispatch(
        flickDirection: FlickDirection,
        fallbackAction: KeyAction?,
        resolved: ResolvedSumireSpecialKeyAction
    ): DispatchResult {
        val dispatched = mutableListOf<Pair<KeyAction, Boolean>>()
        val result = dispatchSumireSpecialKeyRuntimeAction(
            keyData = specialKey,
            flickDirection = flickDirection,
            fallbackAction = fallbackAction,
            isFlick = flickDirection != FlickDirection.TAP,
            resolve = { _, _ -> resolved }
        ) { action, isFlick ->
            dispatched += action to isFlick
        }
        return DispatchResult(result, dispatched)
    }

    private data class DispatchResult(
        val result: com.kazumaproject.custom_keyboard.data.SumireSpecialKeyRuntimeDispatchResult,
        val dispatched: List<Pair<KeyAction, Boolean>>
    )

    private val specialKey = KeyData(
        label = "Special",
        row = 0,
        column = 0,
        isFlickable = false,
        action = KeyAction.Delete,
        isSpecialKey = true,
        keyId = "special_key",
        keyType = KeyType.CROSS_FLICK
    )
}
