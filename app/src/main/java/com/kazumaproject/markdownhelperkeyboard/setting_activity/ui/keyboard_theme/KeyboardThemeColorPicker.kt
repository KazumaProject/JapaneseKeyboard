package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.color.colorChooser
import com.kazumaproject.markdownhelperkeyboard.R

/** Shared by the theme and candidate appearance editors. */
@SuppressLint("CheckResult")
internal fun showKeyboardThemeColorPicker(context: Context, initialColor: Int, onColorSelected: (Int) -> Unit) {
    val palette = intArrayOf(
        Color.TRANSPARENT,
        ContextCompat.getColor(context, com.kazumaproject.core.R.color.blue),
        ContextCompat.getColor(context, com.kazumaproject.core.R.color.char_in_edit_color),
        ContextCompat.getColor(context, com.kazumaproject.core.R.color.orange),
        ContextCompat.getColor(context, com.kazumaproject.core.R.color.violet),
        ContextCompat.getColor(context, com.kazumaproject.core.R.color.violet_light),
        ContextCompat.getColor(context, com.kazumaproject.core.R.color.violet_dark),
        ContextCompat.getColor(context, com.kazumaproject.core.R.color.mint),
        ContextCompat.getColor(context, com.kazumaproject.core.R.color.mint_light),
        ContextCompat.getColor(context, com.kazumaproject.core.R.color.mint_dark),
        ContextCompat.getColor(context, com.kazumaproject.core.R.color.sky),
        ContextCompat.getColor(context, com.kazumaproject.core.R.color.sky_light),
        ContextCompat.getColor(context, com.kazumaproject.core.R.color.sky_dark),
        ContextCompat.getColor(context, com.kazumaproject.core.R.color.orange2),
        ContextCompat.getColor(context, com.kazumaproject.core.R.color.orange_light),
        ContextCompat.getColor(context, com.kazumaproject.core.R.color.orange_dark),
        Color.WHITE, Color.BLACK, Color.DKGRAY, Color.LTGRAY,
        "#1C1C1E".toColorInt(), "#1E2022".toColorInt(),
    )
    MaterialDialog(context).show {
        title(text = context.getString(R.string.keyboard_theme_dialog_title_2))
        colorChooser(colors = palette, initialSelection = initialColor, allowCustomArgb = true) { _, color -> onColorSelected(color) }
        positiveButton(android.R.string.ok)
        negativeButton(android.R.string.cancel)
    }
}
