package com.kazumaproject.markdownhelperkeyboard.setting_activity

internal object FlickPreviewDelaySettings {
    const val KEY = "flick_editor_preview_delay_ms"
    const val MIN_MS = 0
    const val MAX_MS = 500
    const val STEP_MS = 5

    fun normalize(value: Int): Int {
        val bounded = value.coerceIn(MIN_MS, MAX_MS)
        return (bounded + STEP_MS / 2) / STEP_MS * STEP_MS
    }
}
