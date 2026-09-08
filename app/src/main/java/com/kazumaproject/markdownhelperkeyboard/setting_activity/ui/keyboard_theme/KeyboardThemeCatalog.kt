package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme

import androidx.annotation.StringRes
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.markdownhelperkeyboard.R

/** Add a registered skin and its localized presentation here to extend the picker. */
data class KeyboardThemeOption(
    val id: KeyboardSkinId,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
)

object KeyboardThemeCatalog {
    val options = listOf(
        KeyboardThemeOption(KeyboardSkinId.DEFAULT, R.string.keyboard_skin_default,
            R.string.keyboard_skin_default_description),
        KeyboardThemeOption(KeyboardSkinId.CUPERTINO_LIGHT, R.string.keyboard_skin_cupertino_light,
            R.string.keyboard_skin_light_description),
        KeyboardThemeOption(KeyboardSkinId.CUPERTINO_DARK, R.string.keyboard_skin_cupertino_dark,
            R.string.keyboard_skin_dark_description),
    )

    fun find(id: KeyboardSkinId): KeyboardThemeOption = options.first { it.id == id }
}
