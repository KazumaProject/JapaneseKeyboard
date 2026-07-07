package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut

import androidx.annotation.StringRes
import com.kazumaproject.markdownhelperkeyboard.R

enum class PhysicalKeyboardShortcutAction(
    val id: String,
    @StringRes val labelResId: Int,
    val allowedContexts: Set<PhysicalKeyboardShortcutContext>
) {
    COPY("copy", R.string.physical_keyboard_shortcut_action_copy, setOf(PhysicalKeyboardShortcutContext.ANY)),
    PASTE("paste", R.string.physical_keyboard_shortcut_action_paste, setOf(PhysicalKeyboardShortcutContext.ANY)),
    CUT("cut", R.string.physical_keyboard_shortcut_action_cut, setOf(PhysicalKeyboardShortcutContext.ANY)),
    SELECT_ALL("select_all", R.string.physical_keyboard_shortcut_action_select_all, setOf(PhysicalKeyboardShortcutContext.ANY)),
    SWITCH_TO_JAPANESE("switch_to_japanese", R.string.physical_keyboard_shortcut_action_switch_to_japanese, setOf(PhysicalKeyboardShortcutContext.ANY)),
    SWITCH_TO_ENGLISH(
        "switch_to_english",
        R.string.physical_keyboard_shortcut_action_switch_to_english,
        setOf(PhysicalKeyboardShortcutContext.ANY, PhysicalKeyboardShortcutContext.COMPOSITION)
    ),
    CYCLE_INPUT_MODE("cycle_input_mode", R.string.physical_keyboard_shortcut_action_cycle_input_mode, setOf(PhysicalKeyboardShortcutContext.ANY, PhysicalKeyboardShortcutContext.COMPOSITION)),
    CONVERT("convert", R.string.physical_keyboard_shortcut_action_convert, setOf(PhysicalKeyboardShortcutContext.COMPOSITION)),
    COMMIT(
        "commit",
        R.string.physical_keyboard_shortcut_action_commit,
        setOf(PhysicalKeyboardShortcutContext.COMPOSITION, PhysicalKeyboardShortcutContext.CONVERSION, PhysicalKeyboardShortcutContext.BUNSETSU_CONVERSION)
    ),
    CANCEL(
        "cancel",
        R.string.physical_keyboard_shortcut_action_cancel,
        setOf(PhysicalKeyboardShortcutContext.COMPOSITION, PhysicalKeyboardShortcutContext.CONVERSION, PhysicalKeyboardShortcutContext.BUNSETSU_CONVERSION)
    ),
    CONVERT_TO_HIRAGANA("convert_to_hiragana", R.string.physical_keyboard_shortcut_action_convert_to_hiragana, setOf(PhysicalKeyboardShortcutContext.COMPOSITION)),
    CONVERT_TO_FULL_KATAKANA("convert_to_full_katakana", R.string.physical_keyboard_shortcut_action_convert_to_full_katakana, setOf(PhysicalKeyboardShortcutContext.COMPOSITION)),
    CONVERT_TO_HALF_WIDTH("convert_to_half_width", R.string.physical_keyboard_shortcut_action_convert_to_half_width, setOf(PhysicalKeyboardShortcutContext.COMPOSITION)),
    CONVERT_TO_FULL_ALPHANUMERIC("convert_to_full_alphanumeric", R.string.physical_keyboard_shortcut_action_convert_to_full_alphanumeric, setOf(PhysicalKeyboardShortcutContext.COMPOSITION)),
    CONVERT_TO_HALF_ALPHANUMERIC("convert_to_half_alphanumeric", R.string.physical_keyboard_shortcut_action_convert_to_half_alphanumeric, setOf(PhysicalKeyboardShortcutContext.COMPOSITION)),
    CONVERT_NEXT("convert_next", R.string.physical_keyboard_shortcut_action_convert_next, setOf(PhysicalKeyboardShortcutContext.CONVERSION, PhysicalKeyboardShortcutContext.BUNSETSU_CONVERSION)),
    CONVERT_PREV("convert_prev", R.string.physical_keyboard_shortcut_action_convert_prev, setOf(PhysicalKeyboardShortcutContext.CONVERSION, PhysicalKeyboardShortcutContext.BUNSETSU_CONVERSION)),
    SEGMENT_FOCUS_LEFT("segment_focus_left", R.string.physical_keyboard_shortcut_action_segment_focus_left, setOf(PhysicalKeyboardShortcutContext.BUNSETSU_CONVERSION)),
    SEGMENT_FOCUS_RIGHT("segment_focus_right", R.string.physical_keyboard_shortcut_action_segment_focus_right, setOf(PhysicalKeyboardShortcutContext.BUNSETSU_CONVERSION)),
    SEGMENT_WIDTH_SHRINK("segment_width_shrink", R.string.physical_keyboard_shortcut_action_segment_width_shrink, setOf(PhysicalKeyboardShortcutContext.BUNSETSU_CONVERSION)),
    SEGMENT_WIDTH_EXPAND("segment_width_expand", R.string.physical_keyboard_shortcut_action_segment_width_expand, setOf(PhysicalKeyboardShortcutContext.BUNSETSU_CONVERSION));

    fun isAllowedIn(context: PhysicalKeyboardShortcutContext): Boolean {
        return allowedContexts.contains(context)
    }

    companion object {
        fun fromId(id: String?): PhysicalKeyboardShortcutAction? {
            return entries.firstOrNull { it.id == id }
        }

        fun availableFor(context: PhysicalKeyboardShortcutContext): List<PhysicalKeyboardShortcutAction> {
            return entries.filter { it.isAllowedIn(context) }
        }
    }
}
