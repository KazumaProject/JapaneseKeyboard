package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.graphics.Rect
import android.view.inputmethod.CursorAnchorInfo
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class PhysicalKeyboardCursorAnchor(
    val x: Float,
    val top: Float,
    val bottom: Float,
    val isRtl: Boolean,
)

internal data class PhysicalKeyboardPopupAnchors(
    val cursor: PhysicalKeyboardCursorAnchor,
    val candidate: PhysicalKeyboardCursorAnchor?,
)

internal data class PhysicalKeyboardPopupPosition(val x: Int, val y: Int, val height: Int)

/** The candidate anchor stays at the start of a composition; the mode anchor always follows the caret. */
internal class PhysicalKeyboardPopupPositionTracker {
    var candidateAnchor: PhysicalKeyboardCursorAnchor? = null
        private set

    fun update(
        anchorInfo: CursorAnchorInfo?,
        hasComposingText: Boolean,
    ): PhysicalKeyboardPopupAnchors? {
        if (!hasComposingText) resetCandidateAnchor()
        if (anchorInfo == null) return null
        val flags = anchorInfo.insertionMarkerFlags
        if (flags and CursorAnchorInfo.FLAG_HAS_INVISIBLE_REGION != 0 &&
            flags and CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION == 0
        ) return null

        val x = anchorInfo.insertionMarkerHorizontal
        val top = anchorInfo.insertionMarkerTop
        val bottom = anchorInfo.insertionMarkerBottom.takeIf(Float::isFinite) ?: top
        val points = floatArrayOf(x, top, x, bottom)
        if (!points.all(Float::isFinite)) return null
        anchorInfo.matrix.mapPoints(points)
        if (!points.all(Float::isFinite)) return null

        val cursor = PhysicalKeyboardCursorAnchor(
            x = points[0],
            top = min(points[1], points[3]),
            bottom = max(points[1], points[3]),
            isRtl = flags and CursorAnchorInfo.FLAG_IS_RTL != 0,
        )
        if (hasComposingText) {
            val first = candidateAnchor
            candidateAnchor = cursor.copy(
                x = first?.x ?: cursor.x,
                isRtl = first?.isRtl ?: cursor.isRtl,
            )
        }
        return PhysicalKeyboardPopupAnchors(cursor, candidateAnchor)
    }

    fun resetCandidateAnchor() {
        candidateAnchor = null
    }

    fun reset() = resetCandidateAnchor()
}

/** Places a measured popup next to the caret inside the visible window bounds. */
internal object PhysicalKeyboardPopupPlacement {
    fun resolve(
        anchor: PhysicalKeyboardCursorAnchor,
        popupWidth: Int,
        popupHeight: Int,
        safeArea: Rect,
        gapPx: Int,
        minimumVisibleHeight: Int = 1,
    ): PhysicalKeyboardPopupPosition? {
        if (safeArea.isEmpty || popupWidth <= 0 || popupHeight <= 0) return null
        if (anchor.x < safeArea.left || anchor.x > safeArea.right ||
            anchor.bottom < safeArea.top || anchor.top > safeArea.bottom
        ) return null
        val width = popupWidth.coerceAtMost(safeArea.width())
        val height = popupHeight.coerceAtMost(safeArea.height())
        val preferredX = anchor.x.roundToInt() - if (anchor.isRtl) width else 0
        val x = preferredX.coerceIn(safeArea.left, safeArea.right - width)

        val below = max(safeArea.top, anchor.bottom.roundToInt() + gapPx)
        val aboveEnd = min(safeArea.bottom, anchor.top.roundToInt() - gapPx)
        val belowSpace = max(0, safeArea.bottom - below)
        val aboveSpace = max(0, aboveEnd - safeArea.top)
        // Keep the badge below the caret when possible; flip only to keep it fully visible.
        val placeBelow = belowSpace >= height || (aboveSpace < height && belowSpace >= aboveSpace)
        val availableHeight = if (placeBelow) belowSpace else aboveSpace
        if (availableHeight < minimumVisibleHeight) return null
        val visibleHeight = min(height, availableHeight)
        val y = if (placeBelow) below else aboveEnd - visibleHeight
        return PhysicalKeyboardPopupPosition(x, y, visibleHeight)
    }
}
