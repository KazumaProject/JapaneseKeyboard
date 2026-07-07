package com.kazumaproject.markdownhelperkeyboard.sumire_special_key

import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.ResolvedSumireSpecialKeyAction
import com.kazumaproject.custom_keyboard.data.SumireSpecialKeyDirection
import com.kazumaproject.custom_keyboard.data.dispatchSumireSpecialKeyRuntimeAction
import com.kazumaproject.custom_keyboard.data.toSumireSpecialKeyDirectionOrNull
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * createFinalLayout 後の対象キーが、KeyType.NORMAL 経路に落ちず CROSS_FLICK 経路で
 * direction 付き dispatch に到達することを runtime dispatcher 経由で検証する。
 */
class SumireSpecialKeyRuntimeDispatchForActiveLayoutTest {
    @Test
    fun targetSpecialKeysAreCrossFlickAndHaveBaseFlickMapForControllerAttach() {
        TARGET_KEY_IDS.forEach { keyId ->
            val layout = layoutFor("switch-mode-effective", KeyboardInputMode.HIRAGANA)
            val keyData = layout.requireKey(keyId)
            // FlickKeyboardView の KeyType.NORMAL 経路を回避するため CROSS_FLICK である。
            assertEquals(
                "$keyId keyType must be CROSS_FLICK",
                KeyType.CROSS_FLICK,
                keyData.keyType
            )
            // FlickKeyboardView は keyId で flickKeyMaps を引いて CrossFlickInputController を attach する。
            assertNotNull(
                "$keyId must have a base flick map under flickKeyMaps[keyId]",
                layout.flickKeyMaps[keyId]
            )
        }
    }

    @Test
    fun enterKeyTapOverrideReachesOnActionWithIsFlickFalse() {
        val layout = layoutFor("toggle", KeyboardInputMode.HIRAGANA)
        val enter = layout.requireKey("enter_key")
        val dispatched = mutableListOf<Pair<KeyAction, Boolean>>()

        val result = dispatchSumireSpecialKeyRuntimeAction(
            keyData = enter,
            flickDirection = FlickDirection.TAP,
            fallbackAction = enter.action,
            isFlick = false,
            resolve = { _, direction ->
                if (direction == SumireSpecialKeyDirection.TAP) {
                    ResolvedSumireSpecialKeyAction.Action(KeyAction.Paste)
                } else {
                    ResolvedSumireSpecialKeyAction.Default
                }
            }
        ) { action, isFlick -> dispatched += action to isFlick }

        assertEquals(SumireSpecialKeyDirection.TAP, result.sumireDirection)
        assertTrue(result.handled)
        assertEquals(listOf(KeyAction.Paste to false), dispatched)
    }

    @Test
    fun enterKeyUpOverrideReachesOnActionWithIsFlickTrue() {
        val layout = layoutFor("toggle", KeyboardInputMode.HIRAGANA)
        val enter = layout.requireKey("enter_key")
        val dispatched = mutableListOf<Pair<KeyAction, Boolean>>()

        // FlickDirection.UP → SumireSpecialKeyDirection.UP の変換
        assertEquals(SumireSpecialKeyDirection.UP, FlickDirection.UP.toSumireSpecialKeyDirectionOrNull())

        dispatchSumireSpecialKeyRuntimeAction(
            keyData = enter,
            flickDirection = FlickDirection.UP,
            fallbackAction = null,
            isFlick = true,
            resolve = { _, direction ->
                if (direction == SumireSpecialKeyDirection.UP) {
                    ResolvedSumireSpecialKeyAction.Action(KeyAction.Delete)
                } else {
                    ResolvedSumireSpecialKeyAction.Default
                }
            }
        ) { action, isFlick -> dispatched += action to isFlick }

        assertEquals(listOf(KeyAction.Delete to true), dispatched)
    }

    @Test
    fun enterKeyRightOverrideMapsFromUpRightFar() {
        val layout = layoutFor("toggle", KeyboardInputMode.HIRAGANA)
        val enter = layout.requireKey("enter_key")
        val dispatched = mutableListOf<Pair<KeyAction, Boolean>>()

        assertEquals(
            SumireSpecialKeyDirection.RIGHT,
            FlickDirection.UP_RIGHT_FAR.toSumireSpecialKeyDirectionOrNull()
        )

        dispatchSumireSpecialKeyRuntimeAction(
            keyData = enter,
            flickDirection = FlickDirection.UP_RIGHT_FAR,
            fallbackAction = null,
            isFlick = true,
            resolve = { _, direction ->
                if (direction == SumireSpecialKeyDirection.RIGHT) {
                    ResolvedSumireSpecialKeyAction.Action(KeyAction.Copy)
                } else {
                    ResolvedSumireSpecialKeyAction.Default
                }
            }
        ) { action, isFlick -> dispatched += action to isFlick }

        assertEquals(listOf(KeyAction.Copy to true), dispatched)
    }

    @Test
    fun enterKeyLeftOverrideMapsFromUpLeftFar() {
        val layout = layoutFor("toggle", KeyboardInputMode.HIRAGANA)
        val enter = layout.requireKey("enter_key")
        val dispatched = mutableListOf<Pair<KeyAction, Boolean>>()

        assertEquals(
            SumireSpecialKeyDirection.LEFT,
            FlickDirection.UP_LEFT_FAR.toSumireSpecialKeyDirectionOrNull()
        )

        dispatchSumireSpecialKeyRuntimeAction(
            keyData = enter,
            flickDirection = FlickDirection.UP_LEFT_FAR,
            fallbackAction = null,
            isFlick = true,
            resolve = { _, direction ->
                if (direction == SumireSpecialKeyDirection.LEFT) {
                    ResolvedSumireSpecialKeyAction.Action(KeyAction.SelectAll)
                } else {
                    ResolvedSumireSpecialKeyAction.Default
                }
            }
        ) { action, isFlick -> dispatched += action to isFlick }

        assertEquals(listOf(KeyAction.SelectAll to true), dispatched)
    }

    @Test
    fun enterKeyDownOverrideReachesOnActionWithIsFlickTrue() {
        val layout = layoutFor("toggle", KeyboardInputMode.HIRAGANA)
        val enter = layout.requireKey("enter_key")
        val dispatched = mutableListOf<Pair<KeyAction, Boolean>>()

        dispatchSumireSpecialKeyRuntimeAction(
            keyData = enter,
            flickDirection = FlickDirection.DOWN,
            fallbackAction = null,
            isFlick = true,
            resolve = { _, direction ->
                if (direction == SumireSpecialKeyDirection.DOWN) {
                    ResolvedSumireSpecialKeyAction.Action(KeyAction.Space)
                } else {
                    ResolvedSumireSpecialKeyAction.Default
                }
            }
        ) { action, isFlick -> dispatched += action to isFlick }

        assertEquals(listOf(KeyAction.Space to true), dispatched)
    }

    @Test
    fun enterKeyWithoutOverrideOnUpDispatchesNothingWhenFallbackIsNull() {
        // base map に UP が無く override も無いケース。フォールバックも null だと何も dispatch されない
        // = 既存の Tap 動作を壊さない。
        val layout = layoutFor("toggle", KeyboardInputMode.HIRAGANA)
        val enter = layout.requireKey("enter_key")
        val dispatched = mutableListOf<Pair<KeyAction, Boolean>>()

        val result = dispatchSumireSpecialKeyRuntimeAction(
            keyData = enter,
            flickDirection = FlickDirection.UP,
            fallbackAction = null,
            isFlick = true,
            resolve = { _, _ -> ResolvedSumireSpecialKeyAction.Default }
        ) { action, isFlick -> dispatched += action to isFlick }

        assertEquals(SumireSpecialKeyDirection.UP, result.sumireDirection)
        assertTrue(dispatched.isEmpty())
    }

    @Test
    fun deleteKeyTapWithoutOverrideDispatchesFallbackDelete() {
        // delete key flick preference 無効でも、Tap の default fallback が壊れないこと。
        val layout = layoutFor(
            "toggle",
            KeyboardInputMode.HIRAGANA,
            deleteKeyFlickSettings = KeyboardDefaultLayouts.DeleteKeyFlickSettings(
                left = false,
                up = false,
                down = false
            )
        )
        val delete = layout.requireKey("delete_key")
        assertEquals(KeyType.CROSS_FLICK, delete.keyType)
        assertNotNull(layout.flickKeyMaps["delete_key"])
        val dispatched = mutableListOf<Pair<KeyAction, Boolean>>()

        dispatchSumireSpecialKeyRuntimeAction(
            keyData = delete,
            flickDirection = FlickDirection.TAP,
            fallbackAction = delete.action,
            isFlick = false,
            resolve = { _, _ -> ResolvedSumireSpecialKeyAction.Default }
        ) { action, isFlick -> dispatched += action to isFlick }

        assertEquals(listOf(KeyAction.Delete to false), dispatched)
    }

    @Test
    fun deleteKeyDirectionalOverrideWorksEvenWithoutDeleteFlickPreference() {
        val layout = layoutFor(
            "toggle",
            KeyboardInputMode.HIRAGANA,
            deleteKeyFlickSettings = KeyboardDefaultLayouts.DeleteKeyFlickSettings(
                left = false,
                up = false,
                down = false
            )
        )
        val delete = layout.requireKey("delete_key")
        val dispatched = mutableListOf<Pair<KeyAction, Boolean>>()

        dispatchSumireSpecialKeyRuntimeAction(
            keyData = delete,
            flickDirection = FlickDirection.UP_RIGHT_FAR,
            fallbackAction = null,
            isFlick = true,
            resolve = { _, direction ->
                if (direction == SumireSpecialKeyDirection.RIGHT) {
                    ResolvedSumireSpecialKeyAction.Action(KeyAction.Enter)
                } else {
                    ResolvedSumireSpecialKeyAction.Default
                }
            }
        ) { action, isFlick -> dispatched += action to isFlick }

        assertEquals(listOf(KeyAction.Enter to true), dispatched)
    }

    @Test
    fun deleteKeyDeletePreferenceFlicksRemainAccessibleViaDefault() {
        // hasFlickActions=true のときの既存 delete flick behavior が、
        // override 経路の修正で壊れていないことを確認する。
        val layout = layoutFor(
            "toggle",
            KeyboardInputMode.HIRAGANA,
            deleteKeyFlickSettings = KeyboardDefaultLayouts.DeleteKeyFlickSettings(
                left = true,
                up = true,
                down = true
            )
        )
        val delete = layout.requireKey("delete_key")
        val map = layout.flickKeyMaps["delete_key"]?.firstOrNull()
        assertNotNull(map)
        val nonNullMap = map!!
        assertTrue(nonNullMap.containsKey(FlickDirection.UP_LEFT))
        assertTrue(nonNullMap.containsKey(FlickDirection.UP))
        assertTrue(nonNullMap.containsKey(FlickDirection.DOWN))
        // delete_key の keyType も CROSS_FLICK でなければならない。
        assertEquals(KeyType.CROSS_FLICK, delete.keyType)
    }

    @Test
    fun tapDisplayOverrideDoesNotBreakKeyIdFlickMapLookup() {
        // SumireSpecialKeyActionDisplayOverrideApplier 相当の状況をシミュレーションし、
        // Tap 表示 override で label が変わっても layout.flickKeyMaps[keyId] が引き続き有効であることを確認する。
        val layout = layoutFor("toggle", KeyboardInputMode.HIRAGANA)
        val baseEnter = layout.requireKey("enter_key")
        val labelChangedEnter = baseEnter.copy(label = "New Label", action = KeyAction.Delete)
        // keyId / keyType / dynamicStates / isSpecialKey は保持されている前提
        assertEquals("enter_key", labelChangedEnter.keyId)
        assertEquals(KeyType.CROSS_FLICK, labelChangedEnter.keyType)
        assertEquals(baseEnter.dynamicStates, labelChangedEnter.dynamicStates)
        assertTrue(labelChangedEnter.isSpecialKey)
        // label が変わっても keyId 経由で flick map は引ける
        assertNotNull(layout.flickKeyMaps[labelChangedEnter.keyId])
        // 直接 label を引いても "New Label" は存在しない (= keyId 経由でしか引けない設計)
        assertNull(layout.flickKeyMaps[labelChangedEnter.label])
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

    private fun KeyboardLayout.requireKey(keyId: String): KeyData {
        val keyData = items
            .filterIsInstance<KeyItem>()
            .firstOrNull { it.keyData.keyId == keyId }
            ?.keyData
        assertNotNull("$keyId must be present", keyData)
        return keyData!!
    }

    private companion object {
        val TARGET_KEY_IDS = setOf(
            "enter_key",
            "switch_next_ime",
            "katakana_toggle_key",
            "space_convert_key",
            "delete_key"
        )
    }
}
