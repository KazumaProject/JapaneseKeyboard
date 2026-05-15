package com.kazumaproject.custom_keyboard

import com.kazumaproject.custom_keyboard.data.DYNAMIC_SUMIRE_SPECIAL_KEY_TAP_OVERRIDE_DISPLAY_KEY_IDS
import com.kazumaproject.custom_keyboard.data.DisplayAction
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.ResolvedSumireSpecialKeyAction
import com.kazumaproject.custom_keyboard.data.SumireSpecialKeyDirection
import com.kazumaproject.custom_keyboard.data.applyTapOverrideDisplayForDynamicSumireSpecialKey
import com.kazumaproject.custom_keyboard.data.buildSumireSpecialKeyDisplayActionMap
import com.kazumaproject.custom_keyboard.data.dispatchSumireSpecialKeyRuntimeAction
import com.kazumaproject.custom_keyboard.data.refreshSumireSpecialKeyTap
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicSumireSpecialKeyTapOverrideDisplayTest {
    @Test
    fun katakanaToggleKeyWithoutTapOverrideKeepsSwitchToNumberLayoutState() {
        assertDynamicStateWithoutTapOverride(
            keyId = "katakana_toggle_key",
            stateIndex = 0,
            expectedAction = KeyAction.SwitchToNumberLayout,
            expectedLabel = "",
            expectedDrawableResId = com.kazumaproject.core.R.drawable.input_mode_number_select_custom
        )
    }

    @Test
    fun katakanaToggleKeyWithoutTapOverrideKeepsToggleKatakanaState() {
        assertDynamicStateWithoutTapOverride(
            keyId = "katakana_toggle_key",
            stateIndex = 1,
            expectedAction = KeyAction.ToggleKatakana,
            expectedLabel = " カナ",
            expectedDrawableResId = null
        )
    }

    @Test
    fun katakanaToggleKeyTapOverrideReplacesSwitchToNumberLayoutStateLast() {
        assertTapOverrideReplacesDynamicState(
            keyId = "katakana_toggle_key",
            stateIndex = 0,
            expectedBaseAction = KeyAction.SwitchToNumberLayout
        )
    }

    @Test
    fun katakanaToggleKeyTapOverrideReplacesToggleKatakanaStateLast() {
        assertTapOverrideReplacesDynamicState(
            keyId = "katakana_toggle_key",
            stateIndex = 1,
            expectedBaseAction = KeyAction.ToggleKatakana
        )
    }

    @Test
    fun spaceConvertKeyWithoutTapOverrideKeepsSpaceStateAndFallbackMap() {
        val result = assertDynamicStateWithoutTapOverride(
            keyId = "space_convert_key",
            stateIndex = 0,
            expectedAction = KeyAction.Space,
            expectedLabel = "空白",
            expectedDrawableResId = com.kazumaproject.core.R.drawable.baseline_space_bar_24
        )
        assertSpaceConvertFallbackMapIsPreserved(result.layout, KeyAction.Space)
    }

    @Test
    fun spaceConvertKeyWithoutTapOverrideKeepsConvertStateAndFallbackMap() {
        val result = assertDynamicStateWithoutTapOverride(
            keyId = "space_convert_key",
            stateIndex = 1,
            expectedAction = KeyAction.Convert,
            expectedLabel = "変換",
            expectedDrawableResId = null
        )
        assertSpaceConvertFallbackMapIsPreserved(result.layout, KeyAction.Convert)
    }

    @Test
    fun spaceConvertKeyTapOverrideReplacesSpaceStateLastAndKeepsFallbackMap() {
        val result = assertTapOverrideReplacesDynamicState(
            keyId = "space_convert_key",
            stateIndex = 0,
            expectedBaseAction = KeyAction.Space
        )
        assertSpaceConvertFallbackMapIsPreserved(result.layout, KeyAction.Space)
    }

    @Test
    fun spaceConvertKeyTapOverrideReplacesConvertStateLastAndKeepsFallbackMap() {
        val result = assertTapOverrideReplacesDynamicState(
            keyId = "space_convert_key",
            stateIndex = 1,
            expectedBaseAction = KeyAction.Convert
        )
        assertSpaceConvertFallbackMapIsPreserved(result.layout, KeyAction.Convert)
    }

    @Test
    fun enterKeyWithoutTapOverrideKeepsNewLineState() {
        assertDynamicStateWithoutTapOverride(
            keyId = "enter_key",
            stateIndex = 0,
            expectedAction = KeyAction.NewLine,
            expectedLabel = "改行",
            expectedDrawableResId = null
        )
    }

    @Test
    fun enterKeyWithoutTapOverrideKeepsConfirmState() {
        assertDynamicStateWithoutTapOverride(
            keyId = "enter_key",
            stateIndex = 1,
            expectedAction = KeyAction.Confirm,
            expectedLabel = "",
            expectedDrawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_alt_24
        )
    }

    @Test
    fun enterKeyWithoutTapOverrideKeepsEnterState() {
        assertDynamicStateWithoutTapOverride(
            keyId = "enter_key",
            stateIndex = 2,
            expectedAction = KeyAction.Enter,
            expectedLabel = "",
            expectedDrawableResId = com.kazumaproject.core.R.drawable.baseline_keyboard_return_24
        )
    }

    @Test
    fun enterKeyTapOverrideReplacesNewLineStateLast() {
        assertTapOverrideReplacesDynamicState(
            keyId = "enter_key",
            stateIndex = 0,
            expectedBaseAction = KeyAction.NewLine
        )
    }

    @Test
    fun enterKeyTapOverrideReplacesConfirmStateLast() {
        assertTapOverrideReplacesDynamicState(
            keyId = "enter_key",
            stateIndex = 1,
            expectedBaseAction = KeyAction.Confirm
        )
    }

    @Test
    fun enterKeyTapOverrideReplacesEnterStateLast() {
        assertTapOverrideReplacesDynamicState(
            keyId = "enter_key",
            stateIndex = 2,
            expectedBaseAction = KeyAction.Enter
        )
    }

    @Test
    fun switchToNumberTapOverrideUsesSwitchToNumberDisplayForAllDynamicTargets() {
        listOf("enter_key", "space_convert_key", "katakana_toggle_key").forEach { keyId ->
            val dynamicKeyData = dynamicStateKeyData(keyId, stateIndex = 0).keyData
            val finalKeyData = dynamicKeyData.applyTapOverrideDisplayForDynamicSumireSpecialKey(
                displayActions = displayActions,
                resolve = tapOverride(KeyAction.SwitchToNumberLayout)
            )

            assertEquals("$keyId action", KeyAction.SwitchToNumberLayout, finalKeyData.action)
            assertEquals(
                "$keyId drawable",
                com.kazumaproject.core.R.drawable.input_mode_number_select_custom,
                finalKeyData.drawableResId
            )
            assertDispatches(
                keyData = finalKeyData,
                direction = FlickDirection.TAP,
                fallbackAction = dynamicKeyData.action,
                resolver = tapOverride(KeyAction.SwitchToNumberLayout),
                expected = KeyAction.SwitchToNumberLayout to false
            )
        }
    }

    @Test
    fun katakanaToggleStateDoesNotOverwriteNonKatakanaTapOverride() {
        val dynamicKeyData = dynamicStateKeyData("katakana_toggle_key", stateIndex = 1).keyData
        val finalKeyData = dynamicKeyData.applyTapOverrideDisplayForDynamicSumireSpecialKey(
            displayActions = displayActions,
            resolve = tapOverride(KeyAction.Copy)
        )

        assertEquals(KeyAction.ToggleKatakana, dynamicKeyData.action)
        assertEquals(KeyAction.Copy, finalKeyData.action)
        assertEquals("Copy", finalKeyData.label)
        assertEquals(com.kazumaproject.core.R.drawable.content_copy_24dp, finalKeyData.drawableResId)
    }

    @Test
    fun upRightDownLeftOverridesDoNotChangeKeyBodyAndDrivePopupAndRuntime() {
        val cases = listOf(
            SumireSpecialKeyDirection.UP to FlickDirection.UP,
            SumireSpecialKeyDirection.RIGHT to FlickDirection.UP_RIGHT_FAR,
            SumireSpecialKeyDirection.DOWN to FlickDirection.DOWN,
            SumireSpecialKeyDirection.LEFT to FlickDirection.UP_LEFT_FAR
        )

        cases.forEach { (sumireDirection, flickDirection) ->
            val dynamic = dynamicStateKeyData("enter_key", stateIndex = 0)
            val finalKeyData = dynamic.keyData.applyTapOverrideDisplayForDynamicSumireSpecialKey(
                displayActions = displayActions,
                resolve = directionalOverride(sumireDirection, KeyAction.Copy)
            )

            assertEquals("body action for $sumireDirection", dynamic.keyData.action, finalKeyData.action)
            assertEquals("body label for $sumireDirection", dynamic.keyData.label, finalKeyData.label)
            assertEquals(
                "body drawable for $sumireDirection",
                dynamic.keyData.drawableResId,
                finalKeyData.drawableResId
            )

            val popupMap = popupDisplayMap(
                layout = dynamic.layout,
                keyData = finalKeyData,
                resolve = directionalOverride(sumireDirection, KeyAction.Copy)
            )
            assertEquals(KeyAction.Copy, (popupMap[flickDirection] as FlickAction.Action).action)
            assertDispatches(
                keyData = finalKeyData,
                direction = flickDirection,
                fallbackAction = null,
                resolver = directionalOverride(sumireDirection, KeyAction.Copy),
                expected = KeyAction.Copy to true
            )
        }
    }

    @Test
    fun tapAndDirectionalOverridesUseTapForBodyAndDirectionsForPopupAndRuntime() {
        val dynamic = dynamicStateKeyData("enter_key", stateIndex = 0)
        val overrides = mapOf(
            SumireSpecialKeyDirection.TAP to KeyAction.Paste,
            SumireSpecialKeyDirection.UP to KeyAction.Delete,
            SumireSpecialKeyDirection.RIGHT to KeyAction.Enter,
            SumireSpecialKeyDirection.DOWN to KeyAction.Space,
            SumireSpecialKeyDirection.LEFT to KeyAction.Copy
        )
        val resolver = resolver(overrides)
        val finalKeyData = dynamic.keyData.applyTapOverrideDisplayForDynamicSumireSpecialKey(
            displayActions = displayActions,
            resolve = resolver
        )

        assertEquals(KeyAction.Paste, finalKeyData.action)
        assertEquals("Paste", finalKeyData.label)
        assertEquals(com.kazumaproject.core.R.drawable.content_paste_24px, finalKeyData.drawableResId)

        val popupMap = popupDisplayMap(dynamic.layout, finalKeyData, resolver)
        assertEquals(KeyAction.Paste, (popupMap[FlickDirection.TAP] as FlickAction.Action).action)
        assertEquals(KeyAction.Delete, (popupMap[FlickDirection.UP] as FlickAction.Action).action)
        assertEquals(KeyAction.Enter, (popupMap[FlickDirection.UP_RIGHT_FAR] as FlickAction.Action).action)
        assertEquals(KeyAction.Space, (popupMap[FlickDirection.DOWN] as FlickAction.Action).action)
        assertEquals(KeyAction.Copy, (popupMap[FlickDirection.UP_LEFT_FAR] as FlickAction.Action).action)

        listOf(
            FlickDirection.TAP to (KeyAction.Paste to false),
            FlickDirection.UP to (KeyAction.Delete to true),
            FlickDirection.UP_RIGHT_FAR to (KeyAction.Enter to true),
            FlickDirection.DOWN to (KeyAction.Space to true),
            FlickDirection.UP_LEFT_FAR to (KeyAction.Copy to true)
        ).forEach { (flickDirection, expected) ->
            assertDispatches(
                keyData = finalKeyData,
                direction = flickDirection,
                fallbackAction = null,
                resolver = resolver,
                expected = expected
            )
        }
    }

    @Test
    fun defaultNoneInputTextAndMissingMetadataDoNotInventNewBodyDisplayRules() {
        val dynamic = dynamicStateKeyData("enter_key", stateIndex = 1).keyData

        val defaultResult = dynamic.applyTapOverrideDisplayForDynamicSumireSpecialKey(
            displayActions = displayActions,
            resolve = { _, _ -> ResolvedSumireSpecialKeyAction.Default }
        )
        val noneResult = dynamic.applyTapOverrideDisplayForDynamicSumireSpecialKey(
            displayActions = displayActions,
            resolve = { _, _ -> ResolvedSumireSpecialKeyAction.None }
        )
        val inputTextResult = dynamic.applyTapOverrideDisplayForDynamicSumireSpecialKey(
            displayActions = displayActions,
            resolve = { _, _ -> ResolvedSumireSpecialKeyAction.InputText("abc") }
        )
        val missingMetadataResult = dynamic.applyTapOverrideDisplayForDynamicSumireSpecialKey(
            displayActions = emptyList(),
            resolve = tapOverride(KeyAction.Copy)
        )

        assertEquals(dynamic, defaultResult)
        assertEquals(dynamic, noneResult)
        assertEquals(dynamic, inputTextResult)
        assertEquals(KeyAction.Copy, missingMetadataResult.action)
        assertEquals(dynamic.label, missingMetadataResult.label)
        assertEquals(dynamic.drawableResId, missingMetadataResult.drawableResId)
    }

    @Test
    fun onlyRequestedDynamicKeysAreTapOverrideDisplayTargets() {
        assertEquals(
            setOf("enter_key", "space_convert_key", "katakana_toggle_key"),
            DYNAMIC_SUMIRE_SPECIAL_KEY_TAP_OVERRIDE_DISPLAY_KEY_IDS
        )
        assertFalse("switch_next_ime must not be a dynamic display reapply target", "switch_next_ime" in DYNAMIC_SUMIRE_SPECIAL_KEY_TAP_OVERRIDE_DISPLAY_KEY_IDS)
        assertFalse("delete_key must not be a dynamic display reapply target", "delete_key" in DYNAMIC_SUMIRE_SPECIAL_KEY_TAP_OVERRIDE_DISPLAY_KEY_IDS)
        assertFalse("dakuten_toggle_key must not be a dynamic display reapply target", "dakuten_toggle_key" in DYNAMIC_SUMIRE_SPECIAL_KEY_TAP_OVERRIDE_DISPLAY_KEY_IDS)
    }

    @Test
    fun nonTargetSpecialKeysKeepTheirBodyAndExistingFallbacks() {
        val layout = layoutFor(
            dynamicStates = mapOf(
                "enter_key" to 0,
                "dakuten_toggle_key" to 0,
                "katakana_toggle_key" to 0,
                "space_convert_key" to 0
            ),
            deleteKeyFlickSettings = KeyboardDefaultLayouts.DeleteKeyFlickSettings(
                left = false,
                up = false,
                down = false
            )
        )
        listOf("switch_next_ime", "delete_key").forEach { keyId ->
            val keyData = layout.requireKey(keyId)
            val finalKeyData = keyData.applyTapOverrideDisplayForDynamicSumireSpecialKey(
                displayActions = displayActions,
                resolve = tapOverride(KeyAction.Copy)
            )

            assertEquals("$keyId body must stay unchanged", keyData, finalKeyData)
            assertNotNull("$keyId popup/runtime keyId alias must stay available", layout.flickKeyMaps[keyId])
        }

        val noFlickDeleteMap = layout.flickKeyMaps["delete_key"]?.firstOrNull()
        assertNotNull(noFlickDeleteMap)
        assertTrue(noFlickDeleteMap!!.containsKey(FlickDirection.TAP))
        assertFalse(noFlickDeleteMap.containsKey(FlickDirection.UP_LEFT))
        assertFalse(noFlickDeleteMap.containsKey(FlickDirection.UP))
        assertFalse(noFlickDeleteMap.containsKey(FlickDirection.DOWN))

        val withFlick = layoutFor(
            dynamicStates = mapOf(
                "enter_key" to 0,
                "dakuten_toggle_key" to 0,
                "katakana_toggle_key" to 0,
                "space_convert_key" to 0
            ),
            deleteKeyFlickSettings = KeyboardDefaultLayouts.DeleteKeyFlickSettings(
                left = true,
                up = true,
                down = true
            )
        )
        val withFlickDeleteMap = withFlick.flickKeyMaps["delete_key"]?.firstOrNull()
        assertNotNull(withFlickDeleteMap)
        assertTrue(withFlickDeleteMap!!.containsKey(FlickDirection.UP_LEFT))
        assertTrue(withFlickDeleteMap.containsKey(FlickDirection.UP))
        assertTrue(withFlickDeleteMap.containsKey(FlickDirection.DOWN))
    }

    private fun assertDynamicStateWithoutTapOverride(
        keyId: String,
        stateIndex: Int,
        expectedAction: KeyAction,
        expectedLabel: String,
        expectedDrawableResId: Int?
    ): DynamicResult {
        val result = dynamicStateKeyData(keyId, stateIndex)
        val finalKeyData = result.keyData.applyTapOverrideDisplayForDynamicSumireSpecialKey(
            displayActions = displayActions,
            resolve = { _, _ -> ResolvedSumireSpecialKeyAction.Default }
        )

        assertEquals(expectedAction, finalKeyData.action)
        assertEquals(expectedLabel, finalKeyData.label)
        assertEquals(expectedDrawableResId, finalKeyData.drawableResId)
        return result.copy(keyData = finalKeyData)
    }

    private fun assertTapOverrideReplacesDynamicState(
        keyId: String,
        stateIndex: Int,
        expectedBaseAction: KeyAction
    ): DynamicResult {
        val result = dynamicStateKeyData(keyId, stateIndex)
        val dynamicStatesBefore = result.originalKey.dynamicStates
        val finalKeyData = result.keyData.applyTapOverrideDisplayForDynamicSumireSpecialKey(
            displayActions = displayActions,
            resolve = tapOverride(KeyAction.Copy)
        )

        assertEquals(expectedBaseAction, result.keyData.action)
        assertEquals(KeyAction.Copy, finalKeyData.action)
        assertEquals("Copy", finalKeyData.label)
        assertEquals(com.kazumaproject.core.R.drawable.content_copy_24dp, finalKeyData.drawableResId)
        assertEquals(dynamicStatesBefore, result.originalKey.dynamicStates)
        assertEquals(expectedBaseAction, result.originalKey.dynamicStates!![stateIndex].action)
        return result.copy(keyData = finalKeyData)
    }

    private fun dynamicStateKeyData(keyId: String, stateIndex: Int): DynamicResult {
        val layout = layoutFor(dynamicStates = mapOf(keyId to stateIndex))
        val originalKey = layout.requireKey(keyId)
        val state = originalKey.dynamicStates!![stateIndex]
        val dynamicStateKeyData = originalKey.copy(
            label = state.label ?: "",
            action = state.action,
            drawableResId = state.drawableResId
        )
        return DynamicResult(layout, originalKey, dynamicStateKeyData)
    }

    private fun popupDisplayMap(
        layout: KeyboardLayout,
        keyData: KeyData,
        resolve: (KeyData, SumireSpecialKeyDirection) -> ResolvedSumireSpecialKeyAction
    ): Map<FlickDirection, FlickAction> {
        val rawMap = layout.flickKeyMaps[keyData.keyId]?.firstOrNull()
            ?: layout.flickKeyMaps[keyData.label]?.firstOrNull()
        assertNotNull(rawMap)
        return buildSumireSpecialKeyDisplayActionMap(
            keyData = keyData,
            baseMap = rawMap!!.refreshSumireSpecialKeyTap(keyData),
            resolve = resolve
        )
    }

    private fun assertSpaceConvertFallbackMapIsPreserved(
        layout: KeyboardLayout,
        expectedTapAction: KeyAction
    ) {
        val map = layout.flickKeyMaps["space_convert_key"]?.firstOrNull()
        assertNotNull(map)
        assertEquals(expectedTapAction, (map!![FlickDirection.TAP] as FlickAction.Action).action)
        if (expectedTapAction == KeyAction.Space) {
            assertEquals(KeyAction.Space, (map[FlickDirection.UP_LEFT] as FlickAction.Action).action)
        } else {
            assertNull(map[FlickDirection.UP_LEFT])
        }
        assertNull(map[FlickDirection.UP])
        assertNull(map[FlickDirection.UP_RIGHT_FAR])
        assertNull(map[FlickDirection.DOWN])
    }

    private fun assertDispatches(
        keyData: KeyData,
        direction: FlickDirection,
        fallbackAction: KeyAction?,
        resolver: (KeyData, SumireSpecialKeyDirection) -> ResolvedSumireSpecialKeyAction,
        expected: Pair<KeyAction, Boolean>
    ) {
        val dispatched = mutableListOf<Pair<KeyAction, Boolean>>()
        dispatchSumireSpecialKeyRuntimeAction(
            keyData = keyData,
            flickDirection = direction,
            fallbackAction = fallbackAction,
            isFlick = direction != FlickDirection.TAP,
            resolve = resolver
        ) { action, isFlick ->
            dispatched += action to isFlick
        }
        assertEquals(listOf(expected), dispatched)
    }

    private fun tapOverride(
        action: KeyAction
    ): (KeyData, SumireSpecialKeyDirection) -> ResolvedSumireSpecialKeyAction =
        { _, direction ->
            if (direction == SumireSpecialKeyDirection.TAP) {
                ResolvedSumireSpecialKeyAction.Action(action)
            } else {
                ResolvedSumireSpecialKeyAction.Default
            }
        }

    private fun directionalOverride(
        overrideDirection: SumireSpecialKeyDirection,
        action: KeyAction
    ): (KeyData, SumireSpecialKeyDirection) -> ResolvedSumireSpecialKeyAction =
        { _, direction ->
            if (direction == overrideDirection) {
                ResolvedSumireSpecialKeyAction.Action(action)
            } else {
                ResolvedSumireSpecialKeyAction.Default
            }
        }

    private fun resolver(
        overrides: Map<SumireSpecialKeyDirection, KeyAction>
    ): (KeyData, SumireSpecialKeyDirection) -> ResolvedSumireSpecialKeyAction =
        { _, direction ->
            overrides[direction]?.let { ResolvedSumireSpecialKeyAction.Action(it) }
                ?: ResolvedSumireSpecialKeyAction.Default
        }

    private fun layoutFor(
        dynamicStates: Map<String, Int>,
        deleteKeyFlickSettings: KeyboardDefaultLayouts.DeleteKeyFlickSettings =
            KeyboardDefaultLayouts.DeleteKeyFlickSettings()
    ): KeyboardLayout = KeyboardDefaultLayouts.createFinalLayout(
        mode = KeyboardInputMode.HIRAGANA,
        dynamicKeyStates = dynamicStates,
        inputLayoutType = "switch-mode-effective",
        inputStyle = "default",
        deleteKeyFlickSettings = deleteKeyFlickSettings
    )

    private fun KeyboardLayout.requireKey(keyId: String): KeyData {
        val keyData = items.filterIsInstance<KeyItem>()
            .firstOrNull { it.keyData.keyId == keyId }
            ?.keyData
        assertNotNull("$keyId must be present", keyData)
        return keyData!!
    }

    private data class DynamicResult(
        val layout: KeyboardLayout,
        val originalKey: KeyData,
        val keyData: KeyData
    )

    private companion object {
        val displayActions = listOf(
            DisplayAction(
                KeyAction.Copy,
                "Copy",
                com.kazumaproject.core.R.drawable.content_copy_24dp
            ),
            DisplayAction(
                KeyAction.Paste,
                "Paste",
                com.kazumaproject.core.R.drawable.content_paste_24px
            ),
            DisplayAction(
                KeyAction.SwitchToNumberLayout,
                "Number",
                com.kazumaproject.core.R.drawable.input_mode_number_select_custom
            )
        )
    }
}
