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
    val selectedOption: TfbiFlickDirection? = null,
    val fingerPosition: TfbiGuideFingerPosition? = null
)

/**
 * The current touch position expressed as a normalized point in the guide's 3x3 area.
 *
 * The controllers derive this from the touched key's local coordinates. Keeping it normalized
 * lets the guide render at any popup size without coupling the state to Android view dimensions.
 */
data class TfbiGuideFingerPosition(
    val x: Float,
    val y: Float
)
