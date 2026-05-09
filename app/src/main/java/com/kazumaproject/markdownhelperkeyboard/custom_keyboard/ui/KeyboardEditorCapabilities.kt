package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyboardLayout

data class KeyboardEditorCapabilities(
    val showHalfCellControls: Boolean,
    val showInsertionDirectionControls: Boolean,
    val showGridStructuralControls: Boolean
)

fun keyboardEditorCapabilities(layout: KeyboardLayout): KeyboardEditorCapabilities {
    val isAlphabetFlexibleTemplate = layout.isAlphabetFlexibleTemplate()
    return KeyboardEditorCapabilities(
        showHalfCellControls = isAlphabetFlexibleTemplate,
        showInsertionDirectionControls = isAlphabetFlexibleTemplate,
        showGridStructuralControls = true
    )
}

private fun KeyboardLayout.isAlphabetFlexibleTemplate(): Boolean {
    val alphabetPrefixes = listOf("qwerty_", "azerty_", "dvorak_", "colemak_")
    return items.filterIsInstance<KeyItem>().any { item ->
        alphabetPrefixes.any { prefix ->
            item.id.startsWith(prefix) || item.keyData.keyId?.startsWith(prefix) == true
        }
    }
}
