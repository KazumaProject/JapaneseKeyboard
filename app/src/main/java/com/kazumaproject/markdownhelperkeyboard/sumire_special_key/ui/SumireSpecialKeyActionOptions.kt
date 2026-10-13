package com.kazumaproject.markdownhelperkeyboard.sumire_special_key.ui

import com.kazumaproject.custom_keyboard.data.DisplayAction
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.SpecialKeyActionCategory
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.SpecialKeyActionOption
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.DisplayActionUi
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.groupedSpecialKeyActionOptions

internal fun sumireSpecialKeyActionOptions(
    actions: List<DisplayAction>,
    useDefaultLabel: String,
    categoryTitle: (SpecialKeyActionCategory) -> String
): List<SpecialKeyActionOption> {
    val selectableActions = actions.filter {
        it.action !is KeyAction.Text && it.action !is KeyAction.InputText &&
            KeyActionMapper.fromKeyAction(it.action) != null
    }.map { DisplayActionUi(it.displayName, it.action, it.iconResId) }
    return listOf(SpecialKeyActionOption(useDefaultLabel)) +
        groupedSpecialKeyActionOptions(selectableActions, categoryTitle)
}
