package com.kazumaproject.tenkey.extensions

import android.content.res.Configuration
import com.kazumaproject.core.ui.key_window.ArrowDirection
import com.kazumaproject.tenkey.R
import org.junit.Assert.assertEquals
import org.junit.Test

class PopupWindowExtensionTest {

    @Test
    fun portraitBottomRowLongPressGuideIsShiftedAboveTheKeyboardEdge() {
        val offsets = LongPressPopupPosition.entries.associateWith { position ->
            calculateLongPressPopupYOffset(
                position = position,
                anchorId = R.id.key_11,
                orientation = Configuration.ORIENTATION_PORTRAIT,
                popupHeight = 48,
                anchorHeight = 48
            )
        }

        assertEquals(-144, offsets[LongPressPopupPosition.TOP])
        assertEquals(-96, offsets[LongPressPopupPosition.LEFT])
        assertEquals(-96, offsets[LongPressPopupPosition.CENTER])
        assertEquals(-96, offsets[LongPressPopupPosition.RIGHT])
        assertEquals(-48, offsets[LongPressPopupPosition.BOTTOM])
    }

    @Test
    fun portraitBottomRowLongPressGuideUsesTheScaledPopupSpacing() {
        val offsetFor = { position: LongPressPopupPosition ->
            calculateLongPressPopupYOffset(
                position = position,
                anchorId = R.id.key_11,
                orientation = Configuration.ORIENTATION_PORTRAIT,
                popupHeight = 96,
                anchorHeight = 48
            )
        }

        assertEquals(-216, offsetFor(LongPressPopupPosition.TOP))
        assertEquals(-144, offsetFor(LongPressPopupPosition.CENTER))
        assertEquals(-72, offsetFor(LongPressPopupPosition.BOTTOM))
    }

    @Test
    fun portraitNonBottomRowLongPressGuideKeepsItsExistingOffsets() {
        assertEquals(
            -48,
            calculateLongPressPopupYOffset(
                position = LongPressPopupPosition.CENTER,
                anchorId = R.id.key_5,
                orientation = Configuration.ORIENTATION_PORTRAIT,
                popupHeight = 48,
                anchorHeight = 48
            )
        )
        assertEquals(
            0,
            calculateLongPressPopupYOffset(
                position = LongPressPopupPosition.BOTTOM,
                anchorId = R.id.key_5,
                orientation = Configuration.ORIENTATION_PORTRAIT,
                popupHeight = 48,
                anchorHeight = 48
            )
        )
    }

    @Test
    fun landscapeBottomRowLongPressGuideKeepsItsExistingOffsets() {
        assertEquals(
            -48,
            calculateLongPressPopupYOffset(
                position = LongPressPopupPosition.CENTER,
                anchorId = R.id.key_11,
                orientation = Configuration.ORIENTATION_LANDSCAPE,
                popupHeight = 48,
                anchorHeight = 48
            )
        )
        assertEquals(
            0,
            calculateLongPressPopupYOffset(
                position = LongPressPopupPosition.BOTTOM,
                anchorId = R.id.key_11,
                orientation = Configuration.ORIENTATION_LANDSCAPE,
                popupHeight = 48,
                anchorHeight = 48
            )
        )
    }

    @Test
    fun portraitBottomRowFlickBottomIsPlacedAboveTheAnchor() {
        val placement = calculateFlickBottomPopupPlacement(
            anchorId = R.id.key_11,
            orientation = Configuration.ORIENTATION_PORTRAIT,
            popupWidth = 80,
            anchorWidth = 80,
            anchorHeight = 48
        )

        assertEquals(ArrowDirection.BOTTOM_CENTER, placement.arrowDirection)
        assertEquals(0, placement.xOffset)
        assertEquals(-112, placement.yOffset)
    }

    @Test
    fun portraitNonBottomRowFlickBottomKeepsTheDirectionalPlacement() {
        val placement = calculateFlickBottomPopupPlacement(
            anchorId = R.id.key_5,
            orientation = Configuration.ORIENTATION_PORTRAIT,
            popupWidth = 80,
            anchorWidth = 80,
            anchorHeight = 48
        )

        assertEquals(ArrowDirection.TOP_CENTER, placement.arrowDirection)
        assertEquals(0, placement.xOffset)
        assertEquals(-32, placement.yOffset)
    }

    @Test
    fun landscapeBottomRowFlickBottomKeepsTheDirectionalPlacement() {
        val placement = calculateFlickBottomPopupPlacement(
            anchorId = R.id.key_11,
            orientation = Configuration.ORIENTATION_LANDSCAPE,
            popupWidth = 80,
            anchorWidth = 80,
            anchorHeight = 48
        )

        assertEquals(ArrowDirection.TOP_CENTER, placement.arrowDirection)
        assertEquals(-32, placement.yOffset)
    }
}
