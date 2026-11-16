package com.kazumaproject.markdownhelperkeyboard.keyboard_presentation

import android.content.Context
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CandidatePanelColors
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference

internal object KeyboardCandidateColors {
    fun resolve(context: Context, skin: KeyboardSkinId): CandidatePanelColors = with(AppPreference) {
        val custom = if (theme_mode == "custom") CandidatePanelColors(
            custom_theme_bg_color, getCustomThemeCandidateItemBgColor(DEFAULT_CUSTOM_THEME_CANDIDATE_ITEM_BG_COLOR),
            getCustomThemeCandidateTextColor(custom_theme_key_text_color),
            getCustomThemeCandidateItemPressedBgColor(context.getColor(com.kazumaproject.core.R.color.qwety_key_bg_color)),
            custom_theme_special_key_color, custom_theme_special_key_text_color,
            getCustomThemeShortcutIconColor(custom_theme_special_key_text_color)) else null
        CandidatePanelColors.resolve(context, KeyboardSkinRegistry.find(skin)?.palette, custom,
            cupertinoClassic = skin == KeyboardSkinId.CUPERTINO_CLASSIC)
    }
}
