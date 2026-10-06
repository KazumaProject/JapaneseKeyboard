package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import androidx.annotation.StringRes
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.DisplayActionUi

internal enum class SpecialKeyActionCategory(@StringRes val titleResId: Int) {
    INPUT(R.string.special_key_category_input),
    DELETE_AND_CURSOR(R.string.special_key_category_delete_cursor),
    TEXT_AND_MODE(R.string.special_key_category_text_mode),
    KEYBOARD(R.string.special_key_category_keyboard),
    EDIT_AND_OTHER(R.string.special_key_category_edit_other)
}

internal data class SpecialKeyActionOption(
    val label: String,
    val action: DisplayActionUi? = null,
    val isHeader: Boolean = false
)

internal fun groupedSpecialKeyActionOptions(
    actions: List<DisplayActionUi>,
    categoryTitle: (SpecialKeyActionCategory) -> String
): List<SpecialKeyActionOption> = SpecialKeyActionCategory.entries.flatMap { category ->
    val categoryActions = actions.filter { it.action.specialKeyCategory() == category }
        .sortedBy { it.action.specialKeyOrder() }
    if (categoryActions.isEmpty()) emptyList() else {
        listOf(SpecialKeyActionOption(categoryTitle(category), isHeader = true)) +
            categoryActions.map { SpecialKeyActionOption(it.displayName, action = it) }
    }
}

private fun KeyAction.specialKeyCategory(): SpecialKeyActionCategory = when (this) {
    KeyAction.Space,
    KeyAction.CommitAndInsertSpace,
    KeyAction.ForceHalfWidthSpace,
    KeyAction.ForceFullWidthSpace,
    KeyAction.Convert,
    KeyAction.Enter,
    KeyAction.ForceNewLine -> SpecialKeyActionCategory.INPUT

    KeyAction.Delete,
    KeyAction.DeleteUntilSymbol,
    KeyAction.DeleteAfterCursorUntilSymbol,
    KeyAction.DeleteAfterCursor,
    KeyAction.MoveCursorLeft,
    KeyAction.MoveCursorUp,
    KeyAction.MoveCursorDown,
    KeyAction.MoveCursorRight -> SpecialKeyActionCategory.DELETE_AND_CURSOR

    KeyAction.Cut,
    KeyAction.Copy,
    KeyAction.Paste,
    KeyAction.SelectAll -> SpecialKeyActionCategory.EDIT_AND_OTHER

    KeyAction.ToggleDakuten,
    KeyAction.ToggleDakutenOnly,
    KeyAction.ToggleHandakutenOnly,
    KeyAction.ToggleCase,
    KeyAction.ToggleKatakana,
    KeyAction.ShiftKey,
    KeyAction.CapLockKey,
    KeyAction.SwitchDirectMode,
    KeyAction.SwitchRomajiEnglish -> SpecialKeyActionCategory.TEXT_AND_MODE

    KeyAction.SwitchToKanaLayout,
    KeyAction.SwitchToNextIme,
    KeyAction.ShowEmojiKeyboard,
    KeyAction.MoveCustomKeyboardTab,
    is KeyAction.MoveToCustomKeyboard,
    KeyAction.SwitchToEnglishLayout,
    KeyAction.SwitchToNumberLayout -> SpecialKeyActionCategory.KEYBOARD

    else -> SpecialKeyActionCategory.EDIT_AND_OTHER
}

// Stable sorting preserves the existing order for categories without a custom order.
private fun KeyAction.specialKeyOrder(): Int = when (this) {
    KeyAction.SwitchToKanaLayout -> 0
    KeyAction.SwitchToEnglishLayout -> 1
    KeyAction.SwitchToNumberLayout -> 2
    KeyAction.ShowEmojiKeyboard -> 3
    KeyAction.MoveCustomKeyboardTab -> 4
    is KeyAction.MoveToCustomKeyboard -> 5
    KeyAction.SwitchToNextIme -> 6
    KeyAction.Cut -> 0
    KeyAction.Copy -> 1
    KeyAction.Paste -> 2
    KeyAction.SelectAll -> 3
    KeyAction.DoNothing -> Int.MAX_VALUE
    else -> 4
}
