package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import kotlin.math.roundToInt

internal data class ComposingGuidePlacement(
    val xFraction: Float = .5f,
    val yFraction: Float = 1f,
    val widthDp: Float = 280f,
    val heightDp: Float = 112f,
) {
    fun resolve(left: Int, top: Int, availableWidth: Int, availableHeight: Int, density: Float): GuideBounds {
        val width = (widthDp.finiteOr(280f).coerceAtLeast(200f) * density).roundToInt().coerceIn(1, availableWidth.coerceAtLeast(1))
        val height = (heightDp.finiteOr(112f).coerceAtLeast(96f) * density).roundToInt().coerceIn(1, availableHeight.coerceAtLeast(1))
        return GuideBounds(
            left + ((availableWidth - width).coerceAtLeast(0) * xFraction.finiteOr(.5f).coerceIn(0f, 1f)).roundToInt(),
            top + ((availableHeight - height).coerceAtLeast(0) * yFraction.finiteOr(1f).coerceIn(0f, 1f)).roundToInt(),
            width, height,
        )
    }
}

internal fun Float.finiteOr(fallback: Float) = if (isFinite()) this else fallback

internal data class GuideBounds(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right get() = x + width
    val bottom get() = y + height
}
