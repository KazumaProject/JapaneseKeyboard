package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.usesFlexiblePlacement

data class KeyboardEditorCapabilities(
    val showHalfCellControls: Boolean,
    val showInsertionDirectionControls: Boolean,
    val showGridStructuralControls: Boolean
)

fun keyboardEditorCapabilities(layout: KeyboardLayout): KeyboardEditorCapabilities {
    val isFlexibleEditorLayout = layout.isFlexibleEditorLayout()
    return KeyboardEditorCapabilities(
        showHalfCellControls = isFlexibleEditorLayout,
        showInsertionDirectionControls = isFlexibleEditorLayout,
        showGridStructuralControls = !isFlexibleEditorLayout
    )
}

private fun KeyboardLayout.isFlexibleEditorLayout(): Boolean {
    if (isFlexiblePlacementLayout) return true
    if (!usesFlexiblePlacement()) return false
    val alphabetPrefixes = listOf("qwerty_", "azerty_", "dvorak_", "colemak_")
    return items.filterIsInstance<KeyItem>().any { item ->
        alphabetPrefixes.any { prefix ->
            item.id.startsWith(prefix) || item.keyData.keyId?.startsWith(prefix) == true
        }
    }
}
