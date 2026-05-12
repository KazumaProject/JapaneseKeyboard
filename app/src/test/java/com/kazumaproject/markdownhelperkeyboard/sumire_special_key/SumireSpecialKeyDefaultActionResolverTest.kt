package com.kazumaproject.markdownhelperkeyboard.sumire_special_key

import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.SumireSpecialKeyDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class SumireSpecialKeyDefaultActionResolverTest {
    @Test
    fun normalTapDefaultComesFromKeyDataAction() {
        val layout = layout(KeyType.NORMAL, flickKeyMaps = emptyMap())

        assertEquals(
            KeyAction.Space,
            SumireSpecialKeyDefaultActionResolver.resolve(
                layout,
                "special_key",
                SumireSpecialKeyDirection.TAP
            )
        )
        assertEquals(
            null,
            SumireSpecialKeyDefaultActionResolver.resolve(
                layout,
                "special_key",
                SumireSpecialKeyDirection.UP
            )
        )
    }

    @Test
    fun crossFlickDefaultComesFromFlickMapAndPrefersKeyIdOverLabel() {
        val layout = layout(
            KeyType.CROSS_FLICK,
            flickKeyMaps = mapOf(
                "special_key" to listOf(
                    mapOf(
                        FlickDirection.TAP to FlickAction.Action(KeyAction.Delete),
                        FlickDirection.UP_LEFT to FlickAction.Action(KeyAction.DeleteUntilSymbol),
                        FlickDirection.UP_RIGHT_FAR to FlickAction.Action(KeyAction.Enter)
                    )
                ),
                "Space" to listOf(
                    mapOf(FlickDirection.TAP to FlickAction.Action(KeyAction.Paste))
                )
            )
        )

        assertEquals(
            KeyAction.Delete,
            SumireSpecialKeyDefaultActionResolver.resolve(layout, "special_key", SumireSpecialKeyDirection.TAP)
        )
        assertEquals(
            KeyAction.DeleteUntilSymbol,
            SumireSpecialKeyDefaultActionResolver.resolve(layout, "special_key", SumireSpecialKeyDirection.LEFT)
        )
        assertEquals(
            KeyAction.Enter,
            SumireSpecialKeyDefaultActionResolver.resolve(layout, "special_key", SumireSpecialKeyDirection.RIGHT)
        )
    }

    private fun layout(
        keyType: KeyType,
        flickKeyMaps: Map<String, List<Map<FlickDirection, FlickAction>>>
    ): KeyboardLayout {
        val key = KeyItem(
            id = "special_key",
            keyData = KeyData(
                label = "Space",
                row = 0,
                column = 0,
                isFlickable = false,
                action = KeyAction.Space,
                isSpecialKey = true,
                keyId = "special_key",
                keyType = keyType
            ),
            placement = GridPlacement(0, 0, 1, 1)
        )
        return KeyboardLayout(
            keys = listOf(key.keyData),
            flickKeyMaps = flickKeyMaps,
            columnCount = 1,
            rowCount = 1,
            items = listOf(key),
            columnUnitCount = 1,
            rowUnitCount = 1
        )
    }
}

