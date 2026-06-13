package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

object KeyboardTouchEffectType {
    const val NONE = "none"
    const val SUMINAGASHI_INK = "suminagashi_ink"
    const val LIQUID_RIPPLE = "liquid_ripple"

    fun normalize(value: String?): String {
        return when (value) {
            SUMINAGASHI_INK -> SUMINAGASHI_INK
            LIQUID_RIPPLE -> LIQUID_RIPPLE
            else -> NONE
        }
    }

    fun isEnabled(value: String): Boolean {
        return normalize(value) != NONE
    }

    fun isSuminagashi(value: String): Boolean {
        return normalize(value) == SUMINAGASHI_INK
    }

    fun isLiquidRipple(value: String): Boolean {
        return normalize(value) == LIQUID_RIPPLE
    }
}
