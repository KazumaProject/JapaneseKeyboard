package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import android.content.Context
import android.util.TypedValue
import com.google.android.material.color.DynamicColors
import com.kazumaproject.core.ui.skin.SkinPalette

/** Resolved once from the keyboard's themed context, shared by every floating surface. */
internal data class CandidatePanelColors(
    val background: Int,
    val candidate: Int,
    val text: Int,
    val pressed: Int,
    val selection: Int,
    val selectionText: Int,
    val icon: Int = text,
) {
    companion object {
        fun resolve(context: Context, skin: SkinPalette? = null, custom: CandidatePanelColors? = null): CandidatePanelColors {
            if (skin != null) return CandidatePanelColors(skin.background, skin.key, skin.text, skin.pressed, skin.selection, skin.selectionText)
            if (custom != null) return custom
            fun color(id: Int) = context.getColor(id)
            fun themed(attr: Int, fallback: Int, dynamicOnly: Boolean = true): Int {
                if (dynamicOnly && !DynamicColors.isDynamicColorAvailable()) return fallback
                val value = TypedValue()
                return if (context.theme.resolveAttribute(attr, value, true)) {
                    if (value.resourceId != 0) color(value.resourceId) else value.data
                } else fallback
            }
            val text = color(com.kazumaproject.core.R.color.main_text_color)
            val key = color(com.kazumaproject.core.R.color.qwety_key_bg_color)
            return CandidatePanelColors(
                themed(com.google.android.material.R.attr.colorSurfaceContainer, color(com.kazumaproject.core.R.color.keyboard_bg)),
                themed(com.google.android.material.R.attr.colorSurfaceContainerHigh, key),
                text,
                themed(com.google.android.material.R.attr.colorSecondaryContainer, key),
                themed(androidx.appcompat.R.attr.colorPrimary, text, dynamicOnly = false),
                themed(com.google.android.material.R.attr.colorOnPrimary, color(com.kazumaproject.core.R.color.keyboard_bg), dynamicOnly = false),
            )
        }
    }
}
