package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

object KeyboardTouchEffectType {
    const val NONE = "none"
    const val LIQUID_INK = "liquid_ink"
    const val AURORA_INK = "aurora_ink"

    // Kept as a legacy alias for saved preferences. New writes should use LIQUID_INK.
    const val SUMINAGASHI_INK = "suminagashi_ink"

    private const val SUMINAGASHI = "suminagashi"

    const val LIQUID_RIPPLE = "liquid_ripple"
    const val SPRAY_PAINT = "spray_paint"

    fun normalize(value: String?): String {
        return when (value) {
            LIQUID_INK,
            SUMINAGASHI,
            SUMINAGASHI_INK -> LIQUID_INK

            AURORA_INK -> AURORA_INK
            LIQUID_RIPPLE -> LIQUID_RIPPLE
            SPRAY_PAINT -> SPRAY_PAINT
            else -> NONE
        }
    }

    fun isEnabled(value: String): Boolean {
        return normalize(value) != NONE
    }

    fun isLiquidInk(value: String): Boolean {
        return normalize(value) == LIQUID_INK
    }

    fun isAuroraInk(value: String): Boolean {
        return normalize(value) == AURORA_INK
    }

    fun isSuminagashi(value: String): Boolean {
        return isLiquidInk(value)
    }

    fun isLiquidRipple(value: String): Boolean {
        return normalize(value) == LIQUID_RIPPLE
    }

    fun isSprayPaint(value: String): Boolean {
        return normalize(value) == SPRAY_PAINT
    }
}
