package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting.preview

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardPreviewGeometryTest {

    @Test
    fun keyboardWidthPercentRoundTripsWithoutChangingPreference() {
        listOf(32, 41, 65, 97, 98, 99, 100).forEach { percent ->
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
    fun nearFullWidthsUseTheSameTruncationAsTheNormalIme() {
        assertEquals(1058, KeyboardPreviewGeometry.widthForPercent(1080, 98))
        assertEquals(1069, KeyboardPreviewGeometry.widthForPercent(1080, 99))
        assertEquals(1080, KeyboardPreviewGeometry.widthForPercent(1080, 100))
        assertEquals(98, KeyboardPreviewGeometry.percentForWidth(1058, 1080))
        assertEquals(99, KeyboardPreviewGeometry.percentForWidth(1069, 1080))
    }
}
