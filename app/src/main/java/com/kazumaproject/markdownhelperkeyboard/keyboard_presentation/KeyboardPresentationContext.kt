package com.kazumaproject.markdownhelperkeyboard.keyboard_presentation

import android.content.Context
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.core.domain.skin.KeyboardSkinId

/** The keyboard theme must not inherit typography or inflater overrides from the settings activity. */
internal object KeyboardPresentationContext {
    fun create(base: Context, skin: KeyboardSkinId = AppPreference.keyboardSkin): Context {
        if (skin != KeyboardSkinId.DEFAULT || AppPreference.theme_mode == "custom" || !DynamicColors.isDynamicColorAvailable())
            return ContextThemeWrapper(base, R.style.Theme_MarkdownKeyboard)
        val seed = AppPreference.seedColor
        return if (seed == 0) DynamicColors.wrapContextIfAvailable(base, R.style.Theme_MarkdownKeyboard)
        else DynamicColors.wrapContextIfAvailable(ContextThemeWrapper(base, R.style.Theme_MarkdownKeyboard),
            DynamicColorsOptions.Builder().setContentBasedSource(seed).build())
    }
}
