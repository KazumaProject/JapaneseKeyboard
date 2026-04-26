package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut

import androidx.annotation.StringRes
import com.kazumaproject.markdownhelperkeyboard.R

enum class PhysicalKeyboardShortcutContext(
    val id: String,
    @StringRes val labelResId: Int
) {
    ANY("any", R.string.physical_keyboard_shortcut_context_any),
    COMPOSITION("composition", R.string.physical_keyboard_shortcut_context_composition),
    CONVERSION("conversion", R.string.physical_keyboard_shortcut_context_conversion),
    BUNSETSU_CONVERSION("bunsetsu_conversion", R.string.physical_keyboard_shortcut_context_bunsetsu_conversion);

    companion object {
        fun fromId(id: String?): PhysicalKeyboardShortcutContext {
            return entries.firstOrNull { it.id == id } ?: ANY
        }
    }
}
