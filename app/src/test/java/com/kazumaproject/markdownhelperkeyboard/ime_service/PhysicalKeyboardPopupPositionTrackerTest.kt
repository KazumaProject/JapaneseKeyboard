package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.graphics.Matrix
import android.graphics.Rect
import android.view.inputmethod.CursorAnchorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhysicalKeyboardPopupPositionTrackerTest {
    private fun anchor(x: Float, top: Float = 100f, bottom: Float = 120f): CursorAnchorInfo {
        val matrix = Matrix().apply { setTranslate(40f, 60f) }
        return CursorAnchorInfo.Builder()
            .setInsertionMarkerLocation(x, top, top + 10f, bottom, CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION)
            .setMatrix(matrix)
            .build()
    }

    @Test fun modeAnchorTracksCaretWithoutComposition() {
        val tracker = PhysicalKeyboardPopupPositionTracker()
        val first = tracker.update(anchor(100f), hasComposingText = false)!!
        val second = tracker.update(anchor(260f), hasComposingText = false)!!
        assertEquals(140f, first.cursor.x)
        assertEquals(300f, second.cursor.x)
        assertEquals(160f, second.cursor.top)
        assertNull(second.candidate)
    }

    @Test fun candidateKeepsStartXWhileModeFollowsCaret() {
        val tracker = PhysicalKeyboardPopupPositionTracker()
        tracker.update(anchor(100f), hasComposingText = true)
        val next = tracker.update(anchor(260f, 130f, 150f), hasComposingText = true)!!
        assertEquals(300f, next.cursor.x)
        assertEquals(140f, next.candidate!!.x)
        assertEquals(190f, next.candidate.top)
        tracker.update(anchor(260f), hasComposingText = false)
        assertNull(tracker.candidateAnchor)
    }

    @Test fun invalidCursorGeometryIsIgnored() {
        val tracker = PhysicalKeyboardPopupPositionTracker()
        assertNull(tracker.update(anchor(Float.NaN), hasComposingText = false))
    }

    @Test fun popupUsesMeasuredSizeAndInsetsInsteadOfFixedPixelOffset() {
        val safe = Rect(100, 40, 700, 400)
        val caret = PhysicalKeyboardCursorAnchor(650f, 180f, 200f, false)
        assertEquals(
            PhysicalKeyboardPopupPosition(580, 208, 50),
            PhysicalKeyboardPopupPlacement.resolve(caret, 120, 50, safe, 8, 50),
        )
    }

    @Test fun popupUsesTopOnlyWhenBelowDoesNotFit() {
        val safe = Rect(100, 40, 700, 400)
        val caret = PhysicalKeyboardCursorAnchor(300f, 360f, 380f, false)
        assertEquals(
            PhysicalKeyboardPopupPosition(300, 302, 50),
            PhysicalKeyboardPopupPlacement.resolve(caret, 120, 50, safe, 8, 50),
        )
    }

    @Test fun popupStaysInsideNonzeroWindowBounds() {
        val safe = Rect(226, 274, 2500, 1275)
        val caret = PhysicalKeyboardCursorAnchor(230f, 280f, 300f, false)
        assertEquals(
            PhysicalKeyboardPopupPosition(230, 308, 80),
            PhysicalKeyboardPopupPlacement.resolve(caret, 250, 80, safe, 8),
        )
    }

    @Test fun popupIsShortenedInsteadOfCoveringCaretWhenNeitherSideFits() {
        val safe = Rect(0, 0, 400, 200)
        val caret = PhysicalKeyboardCursorAnchor(80f, 90f, 110f, false)
        assertEquals(
            PhysicalKeyboardPopupPosition(80, 118, 82),
            PhysicalKeyboardPopupPlacement.resolve(caret, 120, 180, safe, 8),
        )
    }

    @Test fun modePopupIsHiddenWhenItCannotFitBesideCaret() {
        val safe = Rect(0, 0, 400, 200)
        val caret = PhysicalKeyboardCursorAnchor(80f, 90f, 110f, false)
        assertNull(PhysicalKeyboardPopupPlacement.resolve(caret, 120, 180, safe, 8, 180))
    }

    @Test fun popupIsHiddenWhenCaretIsOutsideVisibleWindow() {
        val safe = Rect(100, 40, 700, 400)
        val caret = PhysicalKeyboardCursorAnchor(750f, 180f, 200f, false)
        assertNull(PhysicalKeyboardPopupPlacement.resolve(caret, 120, 50, safe, 8))
    }
}
