package com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_layout_edit

import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout

class NormalKeyboardLayoutEditSurface(
    override val parent: FrameLayout,
    private val targetView: View,
    private val availableWidthProvider: () -> Int,
) : KeyboardLayoutEditSurfaceAdapter {

    override fun currentBoundsInParent(): Rect {
        return boundsInParent(parent, targetView)
    }

    override fun availableWidthPx(): Int {
        return availableWidthProvider()
    }

    override fun setEditing(isEditing: Boolean) = Unit
}

internal fun boundsInParent(parent: View, target: View): Rect {
    val parentLocation = IntArray(2)
    val targetLocation = IntArray(2)
    parent.getLocationOnScreen(parentLocation)
    target.getLocationOnScreen(targetLocation)
    val left = targetLocation[0] - parentLocation[0]
    val top = targetLocation[1] - parentLocation[1]
    return Rect(
        left,
        top,
        left + target.width,
        top + target.height,
    )
}
