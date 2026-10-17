package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting.preview

import kotlin.math.roundToInt

/** Dimensions shared by the unscaled portrait and landscape size editors. */
internal object KeyboardPreviewGeometry {
    data class Size(val widthPx: Int, val heightPx: Int)

    fun widthForPercent(screenWidthPx: Int, percent: Int): Int =
        (screenWidthPx.coerceAtLeast(1) * (percent.coerceIn(1, 100) / 100f))
            .toInt().coerceAtLeast(1)

    fun percentForWidth(widthPx: Int, screenWidthPx: Int): Int =
        ((widthPx.coerceAtLeast(0).toFloat() / screenWidthPx.coerceAtLeast(1)) * 100f)
            .roundToInt().coerceIn(1, 100)
}
