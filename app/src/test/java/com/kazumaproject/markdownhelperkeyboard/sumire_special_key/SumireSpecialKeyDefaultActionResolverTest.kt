package com.kazumaproject.markdownhelperkeyboard.sumire_special_key

import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.SumireSpecialKeyDirection
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun resolvesAllFiveDirectionsForEnterKey() {
        val layout = layoutFor("toggle", KeyboardInputMode.HIRAGANA)

        val tap = SumireSpecialKeyDefaultActionResolver.resolve(
            layout, "enter_key", SumireSpecialKeyDirection.TAP
        )
        // dynamicStates[0] = NewLine label "改行"
        assertEquals(KeyAction.NewLine, tap)
        // Default flick map base には UP/RIGHT/DOWN/LEFT が無いので null fallback。
        assertNull(SumireSpecialKeyDefaultActionResolver.resolve(layout, "enter_key", SumireSpecialKeyDirection.UP))
        assertNull(SumireSpecialKeyDefaultActionResolver.resolve(layout, "enter_key", SumireSpecialKeyDirection.RIGHT))
        assertNull(SumireSpecialKeyDefaultActionResolver.resolve(layout, "enter_key", SumireSpecialKeyDirection.DOWN))
        assertNull(SumireSpecialKeyDefaultActionResolver.resolve(layout, "enter_key", SumireSpecialKeyDirection.LEFT))
    }

    @Test
    fun resolvesAllFiveDirectionsForSwitchNextIme() {
        val layout = layoutFor("toggle", KeyboardInputMode.HIRAGANA)

        assertEquals(
            KeyAction.SwitchToNextIme,
            SumireSpecialKeyDefaultActionResolver.resolve(layout, "switch_next_ime", SumireSpecialKeyDirection.TAP)
        )
        assertNull(SumireSpecialKeyDefaultActionResolver.resolve(layout, "switch_next_ime", SumireSpecialKeyDirection.UP))
        assertNull(SumireSpecialKeyDefaultActionResolver.resolve(layout, "switch_next_ime", SumireSpecialKeyDirection.RIGHT))
        assertNull(SumireSpecialKeyDefaultActionResolver.resolve(layout, "switch_next_ime", SumireSpecialKeyDirection.DOWN))
        assertNull(SumireSpecialKeyDefaultActionResolver.resolve(layout, "switch_next_ime", SumireSpecialKeyDirection.LEFT))
    }

    @Test
    fun resolvesAllFiveDirectionsForKatakanaToggleKey() {
        val layout = layoutFor("switch-mode-effective", KeyboardInputMode.HIRAGANA)

        // dynamicStates[0] = SwitchToNumberLayout
        assertEquals(
            KeyAction.SwitchToNumberLayout,
            SumireSpecialKeyDefaultActionResolver.resolve(layout, "katakana_toggle_key", SumireSpecialKeyDirection.TAP)
        )
        assertNull(SumireSpecialKeyDefaultActionResolver.resolve(layout, "katakana_toggle_key", SumireSpecialKeyDirection.UP))
        assertNull(SumireSpecialKeyDefaultActionResolver.resolve(layout, "katakana_toggle_key", SumireSpecialKeyDirection.RIGHT))
        assertNull(SumireSpecialKeyDefaultActionResolver.resolve(layout, "katakana_toggle_key", SumireSpecialKeyDirection.DOWN))
        assertNull(SumireSpecialKeyDefaultActionResolver.resolve(layout, "katakana_toggle_key", SumireSpecialKeyDirection.LEFT))
    }

    @Test
    fun resolvesAllFiveDirectionsForSpaceConvertKey() {
        val layout = layoutFor("toggle", KeyboardInputMode.HIRAGANA)

        // dynamicStates[0] = Space
        assertEquals(
            KeyAction.Space,
            SumireSpecialKeyDefaultActionResolver.resolve(layout, "space_convert_key", SumireSpecialKeyDirection.TAP)
        )
        // 既存の "空白" label 用 flick map (TAP=Space, UP_LEFT=Space) を keyId alias として再利用するので
        // LEFT は Space に解決される (既存挙動を維持)。UP / RIGHT / DOWN は flick map に無く null。
        assertEquals(
            KeyAction.Space,
            SumireSpecialKeyDefaultActionResolver.resolve(layout, "space_convert_key", SumireSpecialKeyDirection.LEFT)
        )
        assertNull(SumireSpecialKeyDefaultActionResolver.resolve(layout, "space_convert_key", SumireSpecialKeyDirection.UP))
        assertNull(SumireSpecialKeyDefaultActionResolver.resolve(layout, "space_convert_key", SumireSpecialKeyDirection.RIGHT))
        assertNull(SumireSpecialKeyDefaultActionResolver.resolve(layout, "space_convert_key", SumireSpecialKeyDirection.DOWN))
    }

    @Test
    fun resolvesAllFiveDirectionsForDeleteKeyWithFlickPreference() {
        val layout = layoutFor(
            "toggle",
            KeyboardInputMode.HIRAGANA,
            deleteKeyFlickSettings = KeyboardDefaultLayouts.DeleteKeyFlickSettings(
                left = true,
                up = true,
                down = true
            )
        )

        assertEquals(
            KeyAction.Delete,
            SumireSpecialKeyDefaultActionResolver.resolve(layout, "delete_key", SumireSpecialKeyDirection.TAP)
        )
        // hasFlickActions=true なので、Delete の UP/LEFT/DOWN flick が default として解決される。
        assertEquals(
            KeyAction.DeleteUntilSymbol,
            SumireSpecialKeyDefaultActionResolver.resolve(layout, "delete_key", SumireSpecialKeyDirection.LEFT)
        )
        assertEquals(
            KeyAction.DeleteAfterCursorUntilSymbol,
            SumireSpecialKeyDefaultActionResolver.resolve(layout, "delete_key", SumireSpecialKeyDirection.UP)
        )
        assertEquals(
            KeyAction.UndoLastDelete,
            SumireSpecialKeyDefaultActionResolver.resolve(layout, "delete_key", SumireSpecialKeyDirection.DOWN)
        )
        // RIGHT は flick map に登録されていない → null。
        assertNull(SumireSpecialKeyDefaultActionResolver.resolve(layout, "delete_key", SumireSpecialKeyDirection.RIGHT))
    }

    @Test
    fun deleteKeyResolvesTapEvenWhenFlickPreferenceIsDisabled() {
        // delete key flick preference が無効でも、Sumire 特殊キー action override 用に
        // delete_key は CROSS_FLICK + flickKeyMaps[delete_key] alias を持っており、
        // TAP の default fallback が壊れない。
        val layout = layoutFor(
            "toggle",
            KeyboardInputMode.HIRAGANA,
            deleteKeyFlickSettings = KeyboardDefaultLayouts.DeleteKeyFlickSettings(
                left = false,
                up = false,
                down = false
            )
        )

        assertEquals(
            KeyAction.Delete,
            SumireSpecialKeyDefaultActionResolver.resolve(layout, "delete_key", SumireSpecialKeyDirection.TAP)
        )
        // 上下左右はどれも flick map に無い → null。override が無い場合の挙動は維持される。
        assertNull(SumireSpecialKeyDefaultActionResolver.resolve(layout, "delete_key", SumireSpecialKeyDirection.UP))
        assertNull(SumireSpecialKeyDefaultActionResolver.resolve(layout, "delete_key", SumireSpecialKeyDirection.RIGHT))
        assertNull(SumireSpecialKeyDefaultActionResolver.resolve(layout, "delete_key", SumireSpecialKeyDirection.DOWN))
        assertNull(SumireSpecialKeyDefaultActionResolver.resolve(layout, "delete_key", SumireSpecialKeyDirection.LEFT))
    }

    @Test
    fun crossFlickAttachAliasIsFoundByKeyIdForEveryTargetKey() {
        val layout = layoutFor("toggle", KeyboardInputMode.HIRAGANA)
        listOf("enter_key", "switch_next_ime", "space_convert_key", "delete_key").forEach { keyId ->
            assertNotNull("$keyId must have flickKeyMaps alias", layout.flickKeyMaps[keyId])
        }
        val effectiveLayout = layoutFor("switch-mode-effective", KeyboardInputMode.HIRAGANA)
        listOf(
            "enter_key", "switch_next_ime", "katakana_toggle_key", "space_convert_key", "delete_key"
        ).forEach { keyId ->
            assertNotNull("$keyId must have flickKeyMaps alias", effectiveLayout.flickKeyMaps[keyId])
        }
    }

    private fun layoutFor(
        layoutType: String,
        mode: KeyboardInputMode,
        deleteKeyFlickSettings: KeyboardDefaultLayouts.DeleteKeyFlickSettings =
            KeyboardDefaultLayouts.DeleteKeyFlickSettings()
    ): KeyboardLayout {
        return KeyboardDefaultLayouts.createFinalLayout(
            mode = mode,
            dynamicKeyStates = mapOf(
                "enter_key" to 0,
                "dakuten_toggle_key" to 0,
                "katakana_toggle_key" to 0,
                "space_convert_key" to 0
            ),
            inputLayoutType = layoutType,
            inputStyle = "default",
            deleteKeyFlickSettings = deleteKeyFlickSettings
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
