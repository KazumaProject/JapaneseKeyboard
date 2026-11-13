package com.kazumaproject.custom_keyboard.view

import kotlin.math.min
import kotlin.math.roundToInt

data class CompactFlickGuideSize(val width: Int, val height: Int)

/** The existing two-step guide dimensions, shared with Free Flick. */
fun compactFlickGuideSize(keyWidth: Int,keyHeight: Int,density: Float,sizeScalePercent: Int=100): CompactFlickGuideSize {
    val base=min(keyWidth,keyHeight).coerceAtLeast(1)
    val scale=sizeScalePercent.coerceIn(50,200)/100f
    return CompactFlickGuideSize(
        (base*1.85f*scale).roundToInt().coerceAtLeast((72*density).roundToInt().coerceAtLeast(1)),
        (base*1.65f*scale).roundToInt().coerceAtLeast((68*density).roundToInt().coerceAtLeast(1))
    )
}
