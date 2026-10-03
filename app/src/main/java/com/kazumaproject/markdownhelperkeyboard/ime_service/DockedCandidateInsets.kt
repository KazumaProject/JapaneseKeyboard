package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.inputmethodservice.InputMethodService.Insets
import android.view.ViewGroup

/** Insets and touch regions use IME-window coordinates, not screen coordinates. */
internal fun applyDockedCandidateInsets(
    container: ViewGroup,
    stabilizeHeight: Boolean,
    outInsets: Insets
) {
    val position = IntArray(2)
    val touchableRegion = android.graphics.Region()
    var top: Int? = null
    // Include app-owned overlays too; only the unused transparent area passes through.
    for (index in 0 until container.childCount) {
        val child = container.getChildAt(index)
        if (!child.isShown || child.width == 0 || child.height == 0) continue
        child.getLocationInWindow(position)
        top = minOf(top ?: position[1], position[1])
        touchableRegion.op(
            position[0], position[1], position[0] + child.width, position[1] + child.height,
            android.graphics.Region.Op.UNION
        )
    }
    top?.let { visibleTop ->
        container.getLocationInWindow(position)
        val insetTop = resolveDockedCandidateInsetsTopPx(
            stabilizeHeight = stabilizeHeight,
            reservedTopPx = position[1],
            visibleTopPx = visibleTop
        )
        outInsets.contentTopInsets = insetTop
        outInsets.visibleTopInsets = insetTop
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(touchableRegion)
    }
}
