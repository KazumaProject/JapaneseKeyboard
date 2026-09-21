package com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_layout_edit

import android.graphics.Rect
import android.widget.FrameLayout
import com.kazumaproject.markdownhelperkeyboard.databinding.FloatingKeyboardLayoutBinding

internal class FloatingKeyboardLayoutEditSurface(
    private val binding: FloatingKeyboardLayoutBinding,
    private val panel: com.kazumaproject.markdownhelperkeyboard.ime_service.floating_keyboard.FloatingKeyboardPanel? = null,
    private val availableWidthProvider: () -> Int,
) : KeyboardLayoutEditSurfaceAdapter {

    override val parent: FrameLayout
        get() = binding.floatingKeyboardContainer

    override fun currentBoundsInParent(): Rect {
        return Rect(0, 0, parent.width, parent.height)
    }

    override fun availableWidthPx(): Int {
        return availableWidthProvider()
    }

    override fun startChromeEdit(values: KeyboardLayoutEditValues, callbacks: KeyboardLayoutEditOverlayView.Callbacks): Boolean {
        val chrome = panel ?: return false
        chrome.editCallbacks = callbacks
        return true
    }

    override fun setEditing(isEditing: Boolean) {
        panel?.setEditing(isEditing)
        binding.dragHandle.isEnabled = !isEditing
        binding.dragHandle.alpha = if (isEditing) 0.35f else 1f
    }
}
