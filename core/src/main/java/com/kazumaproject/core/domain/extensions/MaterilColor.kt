package com.kazumaproject.core.domain.extensions

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

/** テーマ属性を解決して ColorInt を返す拡張関数 */
fun Context.getThemeColor(@AttrRes attrRes: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrRes, typedValue, true)
    return typedValue.data
}

/**

 * テーマ属性を安全に解決して ColorInt を返す。

 *

 * resolveAttribute に失敗した場合や、transparent が返った場合は fallbackColor を返す。

 */

@ColorInt

fun Context.getThemeColorOrFallback(
    @AttrRes attrRes: Int, @ColorInt fallbackColor: Int
): Int {
    val typedValue = TypedValue()
    val resolved = theme.resolveAttribute(attrRes, typedValue, true)
    if (!resolved) {
        return fallbackColor
    }
    val color = typedValue.data
    return if (color == Color.TRANSPARENT) {
        fallbackColor
    } else {
        color
    }
}

fun Context.isDarkThemeOn(): Boolean {
    return when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
        Configuration.UI_MODE_NIGHT_YES -> true   // ダークモード
        Configuration.UI_MODE_NIGHT_NO -> false  // ライトモード
        else -> false  // 不定（デフォルトをライト扱い）
    }
}

