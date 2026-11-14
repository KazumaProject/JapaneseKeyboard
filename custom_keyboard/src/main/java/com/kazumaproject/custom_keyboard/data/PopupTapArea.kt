package com.kazumaproject.custom_keyboard.data

enum class PopupTapSizeMode(val preferenceValue: String) {
    KEY("key"), FLICK_SENSITIVITY("flick_sensitivity");

    companion object {
        fun fromPreference(value: String?) = entries.firstOrNull { it.preferenceValue == value } ?: KEY
    }
}

/** Frozen per gesture. Sensitivity changes the rounded tap box, never saved popup placements. */
data class PopupTapArea(val bounds: PopupBounds = PopupBounds.KEY, val followsSensitivity: Boolean = false) {
    fun contains(x: Float, y: Float, keyWidth: Float, keyHeight: Float): Boolean =
        if (followsSensitivity) bounds.contains(x, y, keyWidth, keyHeight)
        else x >= bounds.left && x < bounds.right && y >= bounds.top && y < bounds.bottom

    companion object {
        val KEY = PopupTapArea()

        fun resolve(mode: PopupTapSizeMode, keyWidth: Float, keyHeight: Float, thresholdPx: Float): PopupTapArea {
            if (mode == PopupTapSizeMode.KEY) return KEY
            require(keyWidth > 0f && keyHeight > 0f && thresholdPx.isFinite() && thresholdPx > 0f)
            // Keep the box within the reserved key footprint so saved surrounding regions stay valid.
            val halfWidth = (thresholdPx / keyWidth).coerceAtMost(.5f)
            val halfHeight = (thresholdPx / keyHeight).coerceAtMost(.5f)
            return PopupTapArea(PopupBounds(-halfWidth, -halfHeight, halfWidth, halfHeight), true)
        }
    }
}
