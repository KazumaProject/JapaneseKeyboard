package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting.preview

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Logical dimensions and projection math shared by portrait and landscape keyboard previews. */
internal object KeyboardPreviewGeometry {
    data class Size(val widthPx: Int, val heightPx: Int)

    data class ProjectedDimensions(val heightPx: Int, val bottomMarginPx: Int)

    fun targetSize(windowWidthPx: Int, windowHeightPx: Int, isLandscape: Boolean): Size {
        val shortSide = min(windowWidthPx, windowHeightPx).coerceAtLeast(1)
        val longSide = max(windowWidthPx, windowHeightPx).coerceAtLeast(1)
        return if (isLandscape) Size(longSide, shortSide) else Size(shortSide, longSide)
    }

    fun fitScale(size: Size, availableWidthPx: Int, maxHeightPx: Int, paddingPx: Int): Float {
        val contentWidth = (availableWidthPx - paddingPx * 2).coerceAtLeast(1)
        val contentHeight = (maxHeightPx - paddingPx * 2).coerceAtLeast(1)
        return minOf(
            1f,
            contentWidth.toFloat() / size.widthPx.coerceAtLeast(1),
            contentHeight.toFloat() / size.heightPx.coerceAtLeast(1)
        )
    }

    fun logicalDelta(physicalDeltaPx: Float, scale: Float): Float =
        if (scale > 0f) physicalDeltaPx / scale else 0f

    fun widthForPercent(logicalScreenWidthPx: Int, percent: Int): Int {
        val width = logicalScreenWidthPx.coerceAtLeast(1)
        return if (percent >= 98) width else (width * percent.coerceIn(1, 100) / 100f).roundToInt()
    }

    fun percentForWidth(widthPx: Int, logicalScreenWidthPx: Int): Int {
        val screenWidth = logicalScreenWidthPx.coerceAtLeast(1)
        val percent = ((widthPx.coerceAtLeast(0).toFloat() / screenWidth) * 100f).roundToInt()
        return if (percent >= 98) 100 else percent.coerceIn(1, 100)
    }

    /**
     * Produces a reachable on-screen projection without modifying the saved dimensions.
     * In particular, an excessive saved bottom margin is shortened only for display.
     */
    fun projectHeightAndBottomMargin(
        logicalHeightPx: Int,
        savedHeightPx: Int,
        savedBottomMarginPx: Int,
        minimumHeightPx: Int
    ): ProjectedDimensions {
        val canvasHeight = logicalHeightPx.coerceAtLeast(1)
        val minimumHeight = minimumHeightPx.coerceAtLeast(1).coerceAtMost(canvasHeight)
        val height = savedHeightPx.coerceIn(minimumHeight, canvasHeight)
        val maxBottomMargin = (canvasHeight - height).coerceAtLeast(0)
        val bottomMargin = savedBottomMarginPx.coerceIn(0, maxBottomMargin)
        return ProjectedDimensions(heightPx = height, bottomMarginPx = bottomMargin)
    }
}
