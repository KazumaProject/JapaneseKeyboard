package com.kazumaproject.custom_keyboard

import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.ResolvedSumireSpecialKeyAction
import com.kazumaproject.custom_keyboard.data.SumireSpecialKeyDirection
import com.kazumaproject.custom_keyboard.data.buildSumireSpecialKeyDisplayActionMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SumireSpecialKeyDisplayActionMapTest {
    @Test
    fun addsMissingFlickOverrideDirectionsSoControllerCanCommitThem() {
        val keyData = specialKey()
        val base = mapOf(FlickDirection.TAP to FlickAction.Action(KeyAction.Space))
        val displayMap = buildSumireSpecialKeyDisplayActionMap(keyData, base) { _, direction ->
            when (direction) {
                SumireSpecialKeyDirection.UP -> ResolvedSumireSpecialKeyAction.Action(KeyAction.Delete)
                SumireSpecialKeyDirection.RIGHT -> ResolvedSumireSpecialKeyAction.Action(KeyAction.Enter)
                else -> ResolvedSumireSpecialKeyAction.Default
            }
        }

        assertEquals(FlickAction.Action(KeyAction.Space), displayMap[FlickDirection.TAP])
        assertEquals(FlickAction.Action(KeyAction.Delete), displayMap[FlickDirection.UP])
        assertEquals(FlickAction.Action(KeyAction.Enter), displayMap[FlickDirection.UP_RIGHT_FAR])
    }

    @Test
    fun tapOverrideDoesNotReplaceFlickDirectionsAndFlickOverrideDoesNotReplaceTap() {
        val keyData = specialKey()
        val base = mapOf(
            FlickDirection.TAP to FlickAction.Action(KeyAction.Space),
            FlickDirection.UP to FlickAction.Action(KeyAction.Convert)
        )
        val displayMap = buildSumireSpecialKeyDisplayActionMap(keyData, base) { _, direction ->
            when (direction) {
                SumireSpecialKeyDirection.TAP -> ResolvedSumireSpecialKeyAction.Action(KeyAction.Delete)
                SumireSpecialKeyDirection.UP -> ResolvedSumireSpecialKeyAction.Action(KeyAction.Enter)
                else -> ResolvedSumireSpecialKeyAction.Default
            }
        }

        assertEquals(FlickAction.Action(KeyAction.Delete), displayMap[FlickDirection.TAP])
        assertEquals(FlickAction.Action(KeyAction.Enter), displayMap[FlickDirection.UP])
        assertEquals(null, displayMap[FlickDirection.DOWN])
    }

    @Test
    fun replacesExistingDirectionsAndDoesNotMutateBaseMap() {
        val keyData = specialKey()
        val base = mapOf(
            FlickDirection.TAP to FlickAction.Action(KeyAction.Space),
            FlickDirection.UP to FlickAction.Action(KeyAction.Convert),
            FlickDirection.DOWN to FlickAction.Action(KeyAction.Enter)
        )

        val displayMap = buildSumireSpecialKeyDisplayActionMap(keyData, base) { _, direction ->
            when (direction) {
                SumireSpecialKeyDirection.UP -> ResolvedSumireSpecialKeyAction.Action(KeyAction.Delete)
                SumireSpecialKeyDirection.DOWN -> ResolvedSumireSpecialKeyAction.Action(KeyAction.Paste)
                else -> ResolvedSumireSpecialKeyAction.Default
            }
        }

        assertEquals(FlickAction.Action(KeyAction.Convert), base[FlickDirection.UP])
        assertEquals(FlickAction.Action(KeyAction.Enter), base[FlickDirection.DOWN])
        assertEquals(FlickAction.Action(KeyAction.Delete), displayMap[FlickDirection.UP])
        assertEquals(FlickAction.Action(KeyAction.Paste), displayMap[FlickDirection.DOWN])
    }

    @Test
    fun leftOverrideUsesUpLeftFarInDisplayMap() {
        val keyData = specialKey()
        val base = mapOf(FlickDirection.TAP to FlickAction.Action(KeyAction.Space))

        val displayMap = buildSumireSpecialKeyDisplayActionMap(keyData, base) { _, direction ->
            when (direction) {
                SumireSpecialKeyDirection.LEFT -> ResolvedSumireSpecialKeyAction.Action(KeyAction.Copy)
                else -> ResolvedSumireSpecialKeyAction.Default
            }
        }

        assertEquals(FlickAction.Action(KeyAction.Copy), displayMap[FlickDirection.UP_LEFT_FAR])
    }

    @Test
    fun inputTextOverrideBecomesTextActionAndDefaultOrNoneAddsNoAction() {
        val keyData = specialKey()
        val base = mapOf(FlickDirection.TAP to FlickAction.Action(KeyAction.Space))

        val displayMap = buildSumireSpecialKeyDisplayActionMap(keyData, base) { _, direction ->
            when (direction) {
                SumireSpecialKeyDirection.UP -> ResolvedSumireSpecialKeyAction.InputText("abc")
                SumireSpecialKeyDirection.RIGHT -> ResolvedSumireSpecialKeyAction.None
                SumireSpecialKeyDirection.DOWN -> ResolvedSumireSpecialKeyAction.Default
                else -> ResolvedSumireSpecialKeyAction.Default
            }
        }

        assertEquals(
            FlickAction.Action(KeyAction.Text("abc"), label = "abc"),
            displayMap[FlickDirection.UP]
        )
        assertFalse(displayMap.containsKey(FlickDirection.UP_RIGHT_FAR))
        assertFalse(displayMap.containsKey(FlickDirection.DOWN))
    }

    private fun specialKey() = KeyData(
        label = "Space",
        row = 0,
        column = 0,
        isFlickable = false,
        action = KeyAction.Space,
        isSpecialKey = true,
        keyId = "space_key",
        keyType = KeyType.CROSS_FLICK
    )
}
