package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui

import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_layout_edit.KeyboardLayoutEditConstraints

internal object KeyTextSizePreviewSizing {
    fun heightPx(heightDp: Int, density: Float): Int {
        val boundedHeightDp = heightDp.coerceIn(
            KeyboardLayoutEditConstraints.MinHeightDp,
            KeyboardLayoutEditConstraints.MaxHeightDp,
        )
        return (boundedHeightDp * density).toInt()
    }
}
