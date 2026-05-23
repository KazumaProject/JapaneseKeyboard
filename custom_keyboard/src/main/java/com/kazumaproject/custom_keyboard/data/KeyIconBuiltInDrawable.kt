package com.kazumaproject.custom_keyboard.data

import androidx.annotation.DrawableRes

data class BuiltInKeyIcon(
    val resourceName: String,
    @DrawableRes val resId: Int
)

object KeyIconBuiltInDrawable {
    val allowList: List<BuiltInKeyIcon> = listOf(
        BuiltInKeyIcon("remove", com.kazumaproject.core.R.drawable.remove),
        BuiltInKeyIcon("backspace_24px", com.kazumaproject.core.R.drawable.backspace_24px),
        BuiltInKeyIcon("baseline_keyboard_return_24", com.kazumaproject.core.R.drawable.baseline_keyboard_return_24),
        BuiltInKeyIcon("baseline_space_bar_24", com.kazumaproject.core.R.drawable.baseline_space_bar_24),
        BuiltInKeyIcon("henkan", com.kazumaproject.core.R.drawable.henkan),
        BuiltInKeyIcon("content_paste_24px", com.kazumaproject.core.R.drawable.content_paste_24px),
        BuiltInKeyIcon("content_copy_24dp", com.kazumaproject.core.R.drawable.content_copy_24dp),
        BuiltInKeyIcon("language_24dp", com.kazumaproject.core.R.drawable.language_24dp),
        BuiltInKeyIcon("baseline_emoji_emotions_24", com.kazumaproject.core.R.drawable.baseline_emoji_emotions_24),
        BuiltInKeyIcon("kana_small", com.kazumaproject.core.R.drawable.kana_small),
        BuiltInKeyIcon("kana_small_custom", com.kazumaproject.core.R.drawable.kana_small_custom),
        BuiltInKeyIcon("english_small", com.kazumaproject.core.R.drawable.english_small),
        BuiltInKeyIcon("shift_24px", com.kazumaproject.core.R.drawable.shift_24px),
        BuiltInKeyIcon("shift_fill_24px", com.kazumaproject.core.R.drawable.shift_fill_24px),
        BuiltInKeyIcon("caps_lock", com.kazumaproject.core.R.drawable.caps_lock),
        BuiltInKeyIcon("caps_lock_outline", com.kazumaproject.core.R.drawable.caps_lock_outline),
        BuiltInKeyIcon("keyboard_24px", com.kazumaproject.core.R.drawable.keyboard_24px),
        BuiltInKeyIcon("keyboard_command_key_24px", com.kazumaproject.core.R.drawable.keyboard_command_key_24px),
        BuiltInKeyIcon("baseline_arrow_left_24", com.kazumaproject.core.R.drawable.baseline_arrow_left_24),
        BuiltInKeyIcon("baseline_arrow_right_24", com.kazumaproject.core.R.drawable.baseline_arrow_right_24),
        BuiltInKeyIcon("outline_arrow_left_alt_24", com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24),
        BuiltInKeyIcon("outline_arrow_right_alt_24", com.kazumaproject.core.R.drawable.outline_arrow_right_alt_24),
        BuiltInKeyIcon("outline_arrow_drop_up_24", com.kazumaproject.core.R.drawable.outline_arrow_drop_up_24),
        BuiltInKeyIcon("outline_arrow_drop_down_24", com.kazumaproject.core.R.drawable.outline_arrow_drop_down_24),
        BuiltInKeyIcon("text_select_start_24dp", com.kazumaproject.core.R.drawable.text_select_start_24dp),
        BuiltInKeyIcon("input_mode_english_custom", com.kazumaproject.core.R.drawable.input_mode_english_custom),
        BuiltInKeyIcon("input_mode_japanese_select_custom", com.kazumaproject.core.R.drawable.input_mode_japanese_select_custom),
        BuiltInKeyIcon("input_mode_number_select_custom", com.kazumaproject.core.R.drawable.input_mode_number_select_custom),
        BuiltInKeyIcon("katakana", com.kazumaproject.core.R.drawable.katakana),
        BuiltInKeyIcon("settings_voice_24px", com.kazumaproject.core.R.drawable.settings_voice_24px),
        BuiltInKeyIcon("backspace_24px_until_symbol", com.kazumaproject.core.R.drawable.backspace_24px_until_symbol),
        BuiltInKeyIcon("backspace_24px_after_cursor", com.kazumaproject.core.R.drawable.backspace_24px_after_cursor),
        BuiltInKeyIcon("language_japanese_kana_left_24px", com.kazumaproject.core.R.drawable.language_japanese_kana_left_24px),
        BuiltInKeyIcon("language_japanese_kana_right_24px", com.kazumaproject.core.R.drawable.language_japanese_kana_right_24px),
        BuiltInKeyIcon("language_japanese_kana_left_bold_24px", com.kazumaproject.core.R.drawable.language_japanese_kana_left_bold_24px),
        BuiltInKeyIcon("language_japanese_kana_right_bold_24px", com.kazumaproject.core.R.drawable.language_japanese_kana_right_bold_24px)
    )

    private val byName: Map<String, BuiltInKeyIcon> = allowList.associateBy { it.resourceName }

    fun resolve(resourceName: String?): Int? =
        resourceName?.let { byName[it]?.resId }

    fun isAllowed(resourceName: String?): Boolean =
        resolve(resourceName) != null
}
