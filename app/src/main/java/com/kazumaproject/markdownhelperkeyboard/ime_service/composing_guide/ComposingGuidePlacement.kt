package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import kotlin.math.roundToInt

internal data class ComposingGuidePlacement(
    val xFraction: Float = .5f,
    val yFraction: Float = 1f,
    val widthDp: Float = 280f,
    val heightDp: Float = 264f,
) {
    companion object { const val MIN_WIDTH_DP = 160 }

    fun rebase(oldArea: GuideBounds, newArea: GuideBounds, density: Float, minimumHeightDp: Float = 96f): ComposingGuidePlacement {
        val old = resolve(oldArea.x, oldArea.y, oldArea.width, oldArea.height, density)
        val placement = copy(heightDp = heightDp.coerceAtLeast(minimumHeightDp))
        val resized = placement.resolve(newArea.x, newArea.y, newArea.width, newArea.height, density)
        return placement.copy(
            xFraction = ((old.x - newArea.x).toFloat() / (newArea.width - resized.width).coerceAtLeast(1)).coerceIn(0f, 1f),
            yFraction = ((old.y - newArea.y).toFloat() / (newArea.height - resized.height).coerceAtLeast(1)).coerceIn(0f, 1f),
        )
    }

    fun resolve(left: Int, top: Int, availableWidth: Int, availableHeight: Int, density: Float, minimumHeightDp: Float = 96f): GuideBounds {
        val width = (widthDp.finiteOr(280f).coerceAtLeast(MIN_WIDTH_DP.toFloat()) * density).roundToInt().coerceIn(1, availableWidth.coerceAtLeast(1))
        val height = (heightDp.finiteOr(264f).coerceAtLeast(minimumHeightDp) * density).roundToInt().coerceIn(1, availableHeight.coerceAtLeast(1))
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
