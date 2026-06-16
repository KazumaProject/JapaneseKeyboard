package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import androidx.annotation.ColorInt

internal data class LuminousBlobSettings(
    val enabled: Boolean,
    val colorMode: String,
    @ColorInt val fixedColor: Int,
    val quality: String = KeyboardTouchEffectQuality.HIGH
) {
    val normalizedColorMode: String = normalizeColorMode(colorMode)
    val normalizedQuality: String = KeyboardTouchEffectQuality.normalize(quality)

    companion object {
        const val COLOR_MODE_RANDOM = "random"
        const val COLOR_MODE_FIXED = "fixed"
        const val COLOR_MODE_PALETTE = "palette"
        const val COLOR_MODE_THEME = "theme"

        @ColorInt
        const val DEFAULT_BASE_COLOR: Int = 0xFFFFF36A.toInt()

        @ColorInt
        const val DEFAULT_EDGE_COLOR: Int = 0xFFFFF9A8.toInt()

        @ColorInt
        const val DEFAULT_DEEP_COLOR: Int = 0xFFD6C800.toInt()

        val Disabled = LuminousBlobSettings(
            enabled = false,
            colorMode = COLOR_MODE_RANDOM,
            fixedColor = DEFAULT_BASE_COLOR,
            quality = KeyboardTouchEffectQuality.HIGH
        )

        fun normalizeColorMode(value: String?): String {
            return when (value) {
                COLOR_MODE_FIXED -> COLOR_MODE_FIXED
                COLOR_MODE_THEME -> COLOR_MODE_THEME
                COLOR_MODE_PALETTE -> COLOR_MODE_PALETTE
                else -> COLOR_MODE_RANDOM
            }
        }

        @ColorInt
        fun withoutTransparentAlpha(@ColorInt color: Int): Int {
            val alpha = color ushr 24
            return if (alpha == 0) {
                FULL_ALPHA_MASK.toInt() or (color and RGB_MASK)
            } else {
                color
            }
        }

        private const val FULL_ALPHA_MASK = 0xFF000000
        private const val RGB_MASK = 0x00FFFFFF
    }
}

internal data class LuminousBlobColor(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float
) {
    fun edgeColor(): LuminousBlobColor = mix(White, 0.42f).withMinimumAlpha(0.72f)

    fun deepColor(): LuminousBlobColor = mix(LuminousBlobColor(0.84f, 0.78f, 0f, alpha), 0.42f)
        .withMinimumAlpha(0.62f)

    private fun mix(other: LuminousBlobColor, amount: Float): LuminousBlobColor {
        val t = amount.coerceIn(0f, 1f)
        return LuminousBlobColor(
            red = red + (other.red - red) * t,
            green = green + (other.green - green) * t,
            blue = blue + (other.blue - blue) * t,
            alpha = alpha + (other.alpha - alpha) * t
        )
    }

    private fun withMinimumAlpha(minimum: Float): LuminousBlobColor {
        return copy(alpha = alpha.coerceAtLeast(minimum))
    }

    companion object {
        val DefaultBase = fromColorInt(LuminousBlobSettings.DEFAULT_BASE_COLOR)
        val DefaultEdge = fromColorInt(LuminousBlobSettings.DEFAULT_EDGE_COLOR)
        val DefaultDeep = fromColorInt(LuminousBlobSettings.DEFAULT_DEEP_COLOR)
        private val White = LuminousBlobColor(1f, 1f, 1f, 1f)

        fun fromColorInt(@ColorInt color: Int): LuminousBlobColor {
            return LuminousBlobColor(
                red = ((color shr 16) and 0xFF) / 255f,
                green = ((color shr 8) and 0xFF) / 255f,
                blue = (color and 0xFF) / 255f,
                alpha = ((color ushr 24) / 255f).coerceIn(0.44f, 1f)
            )
        }
    }
}

internal data class LuminousBlobColorSet(
    val base: LuminousBlobColor,
    val edge: LuminousBlobColor,
    val deep: LuminousBlobColor
) {
    companion object {
        val Default = LuminousBlobColorSet(
            base = LuminousBlobColor.DefaultBase,
            edge = LuminousBlobColor.DefaultEdge,
            deep = LuminousBlobColor.DefaultDeep
        )

        fun fromBase(base: LuminousBlobColor): LuminousBlobColorSet {
            return if (base == LuminousBlobColor.DefaultBase) {
                Default
            } else {
                LuminousBlobColorSet(
                    base = base,
                    edge = base.edgeColor(),
                    deep = base.deepColor()
                )
            }
        }
    }
}
