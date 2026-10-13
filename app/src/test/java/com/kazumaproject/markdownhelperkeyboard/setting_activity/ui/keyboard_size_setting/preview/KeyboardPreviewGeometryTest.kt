package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardPreviewGeometryTest {

    @Test
    fun targetSizeSwapsWindowBoundsForLandscapeAndPortrait() {
        assertEquals(
            KeyboardPreviewGeometry.Size(900, 420),
            KeyboardPreviewGeometry.targetSize(420, 900, isLandscape = true)
        )
        assertEquals(
            KeyboardPreviewGeometry.Size(900, 420),
            KeyboardPreviewGeometry.targetSize(900, 420, isLandscape = true)
        )
        assertEquals(
            KeyboardPreviewGeometry.Size(420, 900),
            KeyboardPreviewGeometry.targetSize(420, 900, isLandscape = false)
        )
    }

    @Test
    fun squareWindowHasSameTargetSizeInEitherOrientation() {
        val square = KeyboardPreviewGeometry.Size(600, 600)
        assertEquals(square, KeyboardPreviewGeometry.targetSize(600, 600, isLandscape = true))
        assertEquals(square, KeyboardPreviewGeometry.targetSize(600, 600, isLandscape = false))
    }

    @Test
    fun previewScaleFitsWidthAndHeightIncludingHandlePadding() {
        val geometry = KeyboardPreviewGeometry.Size(900, 420)

        val scale = KeyboardPreviewGeometry.fitScale(
            geometry,
            availableWidthPx = 420,
            maxHeightPx = 300,
            paddingPx = 24
        )

        assertEquals(0.4133f, scale, 0.001f)
        assertTrue(geometry.widthPx * scale <= 420 - 48)
        assertTrue(geometry.heightPx * scale <= 300 - 48)
    }

    @Test
    fun physicalDragDeltaConvertsBackToLogicalPixels() {
        val logicalDelta = KeyboardPreviewGeometry.logicalDelta(18f, scale = 0.3f)

        assertEquals(60f, logicalDelta, 0.001f)
    }

    @Test
    fun keyboardWidthPercentRoundTripsWithoutChangingPreference() {
        listOf(32, 41, 65, 97, 100).forEach { percent ->
            val width = KeyboardPreviewGeometry.widthForPercent(1080, percent)
            assertEquals(percent, KeyboardPreviewGeometry.percentForWidth(width, 1080))
        }
        assertEquals(
            41,
            KeyboardPreviewGeometry.percentForWidth(
                KeyboardPreviewGeometry.widthForPercent(1001, 41),
                1001
            )
        )
    }

    @Test
    fun outOfRangeSavedDimensionsCanBeClampedForDisplayWithoutMutatingStoredValues() {
        val logicalHeightPx = 780

        val projected = KeyboardPreviewGeometry.projectHeightAndBottomMargin(
            logicalHeightPx = logicalHeightPx,
            savedHeightPx = 420,
            savedBottomMarginPx = 900,
            minimumHeightPx = 100
        )

        assertEquals(420, projected.heightPx)
        assertEquals(360, projected.bottomMarginPx)
        assertTrue(projected.heightPx + projected.bottomMarginPx <= logicalHeightPx)
    }
}
