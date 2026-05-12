package com.kazumaproject.markdownhelperkeyboard.sumire_special_key

import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.SumireSpecialKeyDirection
import com.kazumaproject.custom_keyboard.data.copyWithItems
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyActionOverrideEntity

object SumireSpecialKeyActionDisplayOverrideApplier {
    fun apply(
        layout: KeyboardLayout,
        layoutType: String,
        inputMode: String,
        overrides: List<SumireSpecialKeyActionOverrideEntity>,
        displayMetadata: List<SumireSpecialKeyActionDisplayMetadata>
    ): KeyboardLayout {
        val metadataByActionString = displayMetadata.associateBy {
            KeyActionMapper.fromKeyAction(it.action)
        }
        val tapOverrides = overrides
            .filter {
                it.layoutType == layoutType &&
                        it.inputMode == inputMode &&
                        it.direction == SumireSpecialKeyDirection.TAP.name &&
                        it.overrideType == SumireSpecialKeyOverrideType.KEY_ACTION.name &&
                        !it.keyId.isBlank()
            }
            .associateBy { it.keyId }
        if (tapOverrides.isEmpty()) return layout

        fun KeyData.withTapOverrideDisplay(): KeyData {
            if (!isSpecialKey) return this
            val keyId = keyId?.takeIf { it.isNotBlank() } ?: return this
            val override = tapOverrides[keyId] ?: return this
            val action = KeyActionMapper.toKeyAction(override.actionString) ?: return this
            val metadata = metadataByActionString[override.actionString]
            return copy(
                action = action,
                label = metadata?.displayName ?: label,
                drawableResId = metadata?.iconResId
            )
        }

        val newItems = layout.items.map { item ->
            if (item is KeyItem) {
                item.copy(keyData = item.keyData.withTapOverrideDisplay())
            } else {
                item
            }
        }
        return layout.copyWithItems(newItems)
    }
}
