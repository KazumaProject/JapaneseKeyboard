package com.kazumaproject.markdownhelperkeyboard.sumire_special_key

import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardDefaultLayoutsSumireSpecialKeyIdTest {
    @Test
    fun allActiveSumireSpecialKeysHaveStableUniqueKeyIds() {
        layoutTypes.forEach { layoutType ->
            KeyboardInputMode.entries.forEach { inputMode ->
                val layout = KeyboardDefaultLayouts.createFinalLayout(
                    mode = inputMode,
                    dynamicKeyStates = previewDynamicStates,
                    inputLayoutType = layoutType,
                    inputStyle = "default"
                )
                val specialKeys = layout.items
                    .filterIsInstance<KeyItem>()
                    .filter { it.keyData.isSpecialKey }
                assertTrue(
                    "$layoutType/$inputMode has special keys",
                    specialKeys.isNotEmpty()
                )
                assertTrue(
                    "$layoutType/$inputMode contains blank keyId",
                    specialKeys.all { !it.keyData.keyId.isNullOrBlank() }
                )
                val keyIds = specialKeys.map { it.keyData.keyId.orEmpty() }
                assertTrue(
                    "$layoutType/$inputMode contains duplicate keyId: $keyIds",
                    keyIds.size == keyIds.toSet().size
                )
            }
        }
    }

    private companion object {
        val layoutTypes = listOf("toggle", "flick", "switch-mode-effective")
        val previewDynamicStates = mapOf(
            "enter_key" to 0,
            "dakuten_toggle_key" to 0,
            "katakana_toggle_key" to 0,
            "space_convert_key" to 0
        )
    }
}

