package com.kazumaproject.custom_keyboard.data

import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection

/**
 * Render state for the compact TFBi/Arte-style guide shown above the touched key.
 *
 * [optionLabels] contains display-ready labels (usually the suffix after the current
 * output), rather than the complete output stored in the input map.
 */
data class TfbiGuidePopupState(
    val currentText: String,
    val currentSlot: TfbiFlickDirection,
    val optionLabels: Map<TfbiFlickDirection, String> = emptyMap(),
    val selectedOption: TfbiFlickDirection? = null
)
