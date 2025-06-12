package com.kazumaproject.core.domain.extensions

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import android.view.View
import androidx.annotation.AttrRes

/** テーマ属性を解決して ColorInt を返す拡張関数 */
fun Context.getThemeColor(@AttrRes attrRes: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrRes, typedValue, true)
    return typedValue.data
}

fun applySurfaceContainerHighest(view: View) {
    val color =
        view.context.getThemeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest)
    view.setBackgroundColor(color)
}

fun applyColorPrimaryContainer(view: View) {
    val color =
        view.context.getThemeColor(com.google.android.material.R.attr.colorPrimaryContainer)
    view.setBackgroundColor(color)
}

fun applyColorSurface(view: View) {
    val color =
        view.context.getThemeColor(com.google.android.material.R.attr.colorSurface)
    view.setBackgroundColor(color)
}

fun Context.isDarkThemeOn(): Boolean {
    return when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
        Configuration.UI_MODE_NIGHT_YES -> true   // ダークモード
        Configuration.UI_MODE_NIGHT_NO -> false  // ライトモード
        else -> false  // 不定（デフォルトをライト扱い）
    }
}

