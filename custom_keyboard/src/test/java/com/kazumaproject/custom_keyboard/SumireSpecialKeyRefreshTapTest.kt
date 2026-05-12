package com.kazumaproject.custom_keyboard

import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.refreshSumireSpecialKeyTap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class SumireSpecialKeyRefreshTapTest {
    @Test
    fun nonSpecialKeyReturnsSameMap() {
        val base = mapOf(FlickDirection.TAP to FlickAction.Action(KeyAction.Space))
        val refreshed = base.refreshSumireSpecialKeyTap(
            KeyData(
                label = "x",
                row = 0,
                column = 0,
                isFlickable = false,
                action = KeyAction.Delete,
                isSpecialKey = false,
                keyId = "x"
            )
        )
        assertSame(base, refreshed)
    }

    @Test
    fun blankKeyIdReturnsSameMap() {
        val base = mapOf(FlickDirection.TAP to FlickAction.Action(KeyAction.Space))
        val refreshed = base.refreshSumireSpecialKeyTap(
            KeyData(
                label = "x",
                row = 0,
                column = 0,
                isFlickable = false,
                action = KeyAction.Delete,
                isSpecialKey = true,
                keyId = ""
            )
        )
        assertSame(base, refreshed)
    }

    @Test
    fun missingActionReturnsSameMap() {
        val base = mapOf(FlickDirection.TAP to FlickAction.Action(KeyAction.Space))
        val refreshed = base.refreshSumireSpecialKeyTap(
            KeyData(
                label = "x",
                row = 0,
                column = 0,
                isFlickable = false,
                action = null,
                isSpecialKey = true,
                keyId = "enter_key"
            )
        )
        assertSame(base, refreshed)
    }

    @Test
    fun mismatchedTapActionIsReplacedWithCurrentKeyDataAction() {
        val base = mapOf(
            FlickDirection.TAP to FlickAction.Action(KeyAction.NewLine, label = "改行"),
            FlickDirection.UP_LEFT to FlickAction.Action(KeyAction.Space)
        )
        val refreshed = base.refreshSumireSpecialKeyTap(
            KeyData(
                label = "確定",
                row = 0,
                column = 0,
                isFlickable = false,
                action = KeyAction.Enter,
                isSpecialKey = true,
                keyId = "enter_key",
                keyType = KeyType.CROSS_FLICK
            )
        )
        assertNotSame(base, refreshed)
        assertEquals(
            FlickAction.Action(KeyAction.Enter, label = "確定"),
            refreshed[FlickDirection.TAP]
        )
        // 他の direction はそのまま保持される
        assertEquals(base[FlickDirection.UP_LEFT], refreshed[FlickDirection.UP_LEFT])
    }

    @Test
    fun matchingTapActionReturnsSameMapAndDoesNotMutate() {
        val base = mapOf(
            FlickDirection.TAP to FlickAction.Action(KeyAction.NewLine, label = "改行"),
            FlickDirection.UP_LEFT to FlickAction.Action(KeyAction.Space)
        )
        val refreshed = base.refreshSumireSpecialKeyTap(
            KeyData(
                label = "改行",
                row = 0,
                column = 0,
                isFlickable = false,
                action = KeyAction.NewLine,
                isSpecialKey = true,
                keyId = "enter_key",
                keyType = KeyType.CROSS_FLICK
            )
        )
        assertSame(base, refreshed)
    }

    @Test
    fun keepsExistingDrawableWhenKeyDataDrawableIsNull() {
        val base = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.NewLine, label = "改行", drawableResId = 42
            )
        )
        val refreshed = base.refreshSumireSpecialKeyTap(
            KeyData(
                label = "確定",
                row = 0,
                column = 0,
                isFlickable = false,
                action = KeyAction.Enter,
                isSpecialKey = true,
                keyId = "enter_key",
                keyType = KeyType.CROSS_FLICK,
                drawableResId = null
            )
        )
        assertEquals(
            FlickAction.Action(KeyAction.Enter, label = "確定", drawableResId = 42),
            refreshed[FlickDirection.TAP]
        )
    }

    @Test
    fun keyDataDrawableOverridesExistingDrawable() {
        val base = mapOf(
            FlickDirection.TAP to FlickAction.Action(KeyAction.NewLine, drawableResId = 1)
        )
        val refreshed = base.refreshSumireSpecialKeyTap(
            KeyData(
                label = "",
                row = 0,
                column = 0,
                isFlickable = false,
                action = KeyAction.Enter,
                isSpecialKey = true,
                keyId = "enter_key",
                keyType = KeyType.CROSS_FLICK,
                drawableResId = 99
            )
        )
        assertEquals(
            FlickAction.Action(KeyAction.Enter, label = null, drawableResId = 99),
            refreshed[FlickDirection.TAP]
        )
    }
}
