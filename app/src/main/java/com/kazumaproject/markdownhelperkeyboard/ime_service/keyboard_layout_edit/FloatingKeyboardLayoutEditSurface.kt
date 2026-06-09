package com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_layout_edit

import android.graphics.Rect
import android.widget.FrameLayout
import com.kazumaproject.markdownhelperkeyboard.databinding.FloatingKeyboardLayoutBinding

class FloatingKeyboardLayoutEditSurface(
    private val binding: FloatingKeyboardLayoutBinding,
    private val availableWidthProvider: () -> Int,
) : KeyboardLayoutEditSurfaceAdapter {

    override val parent: FrameLayout
        get() = binding.root

    override fun currentBoundsInParent(): Rect {
        return boundsInParent(parent, binding.floatingKeyboardContainer)
    }

    override fun availableWidthPx(): Int {
        return availableWidthProvider()
    }

    override fun setEditing(isEditing: Boolean) {
        binding.dragHandle.isEnabled = !isEditing
        binding.dragHandle.alpha = if (isEditing) 0.35f else 1f
    }
}
