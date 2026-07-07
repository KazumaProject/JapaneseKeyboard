package com.kazumaproject.markdownhelperkeyboard.sumire_special_key

import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.copyWithItems
import com.kazumaproject.custom_keyboard.data.hasPlacementIssues
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyPlacementOverrideEntity

object SumireSpecialKeyPlacementOverrideApplier {
    fun apply(
        layout: KeyboardLayout,
        layoutType: String,
        inputMode: String,
        overrides: List<SumireSpecialKeyPlacementOverrideEntity>
    ): KeyboardLayout {
        val applicable = overrides
            .filter { it.layoutType == layoutType && it.inputMode == inputMode }
            .associateBy { it.keyId }
        if (applicable.isEmpty()) return layout

        val newItems = layout.items.map { item ->
            if (item is KeyItem &&
                item.keyData.isSpecialKey &&
                !item.keyData.keyId.isNullOrBlank()
            ) {
                val override = applicable[item.keyData.keyId]
                if (override != null) {
                    item.copy(
                        placement = GridPlacement(
                            rowUnits = override.rowUnits,
                            columnUnits = override.columnUnits,
                            rowSpanUnits = override.rowSpanUnits,
                            columnSpanUnits = override.columnSpanUnits
                        )
                    )
                } else {
                    item
                }
            } else {
                item
            }
        }

        if (hasPlacementIssues(newItems, layout.rowUnitCount, layout.columnUnitCount)) {
            return layout
        }
        return layout.copyWithItems(newItems)
    }
}

