package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

object KeyboardTouchEffectQuality {
    const val BALANCED = "balanced"
    const val HIGH = "high"
    const val ULTRA = "ultra"
    const val EXTREME = "extreme"

    fun normalize(value: String?): String {
        return when (value) {
            BALANCED -> BALANCED
            HIGH -> HIGH
            ULTRA -> ULTRA
            EXTREME -> EXTREME
            else -> HIGH
        }
    }
}
