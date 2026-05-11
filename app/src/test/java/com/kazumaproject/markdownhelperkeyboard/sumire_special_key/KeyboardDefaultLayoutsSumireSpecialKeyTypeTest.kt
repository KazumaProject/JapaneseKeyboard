package com.kazumaproject.markdownhelperkeyboard.sumire_special_key

import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardDefaultLayoutsSumireSpecialKeyTypeTest {
    @Test
    fun activeSumireLayoutsContainNormalAndCrossFlickSpecialKeys() {
        val typeSets = layoutTypes.flatMap { layoutType ->
            KeyboardInputMode.entries.map { inputMode ->
                KeyboardDefaultLayouts.createFinalLayout(
                    mode = inputMode,
                    dynamicKeyStates = previewDynamicStates,
                    inputLayoutType = layoutType,
                    inputStyle = "default"
                ).items
                    .filterIsInstance<KeyItem>()
                    .filter { it.keyData.isSpecialKey }
                    .map { it.keyData.keyType }
                    .toSet()
            }
        }

        assertTrue(typeSets.any { KeyType.NORMAL in it })
        assertTrue(typeSets.any { KeyType.CROSS_FLICK in it })
    }

    @Test
    fun deleteKeyTypeChangesWithPreferenceButKeyIdIsStable() {
        val noFlick = KeyboardDefaultLayouts.createFinalLayout(
            mode = KeyboardInputMode.HIRAGANA,
            dynamicKeyStates = previewDynamicStates,
            inputLayoutType = "toggle",
            inputStyle = "default",
            deleteKeyFlickSettings = KeyboardDefaultLayouts.DeleteKeyFlickSettings(
                left = false,
                up = false,
                down = false
            )
        ).deleteKey()

        val withFlick = KeyboardDefaultLayouts.createFinalLayout(
            mode = KeyboardInputMode.HIRAGANA,
            dynamicKeyStates = previewDynamicStates,
            inputLayoutType = "toggle",
            inputStyle = "default",
            deleteKeyFlickSettings = KeyboardDefaultLayouts.DeleteKeyFlickSettings(
                left = true,
                up = true,
                down = true
            )
        ).deleteKey()

        assertEquals("delete_key", noFlick.keyData.keyId)
        assertEquals("delete_key", withFlick.keyData.keyId)
        assertEquals(KeyType.NORMAL, noFlick.keyData.keyType)
        assertEquals(KeyType.CROSS_FLICK, withFlick.keyData.keyType)
    }

    private fun com.kazumaproject.custom_keyboard.data.KeyboardLayout.deleteKey(): KeyItem =
        items.filterIsInstance<KeyItem>().first { it.keyData.keyId == "delete_key" }

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

