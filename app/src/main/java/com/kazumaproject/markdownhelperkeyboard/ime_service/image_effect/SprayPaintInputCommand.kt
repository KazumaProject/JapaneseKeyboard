package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.graphics.Color
import androidx.annotation.ColorInt

internal data class SprayPaintSettings(
    val enabled: Boolean,
    val colorMode: String,
    @ColorInt val fixedColor: Int,
    val palette: String,
    val quality: String = KeyboardTouchEffectQuality.HIGH
) {
    val normalizedColorMode: String = normalizeColorMode(colorMode)
    val normalizedPalette: String = normalizePalette(palette)
    val normalizedQuality: String = KeyboardTouchEffectQuality.normalize(quality)

    companion object {
        const val COLOR_MODE_RANDOM = "random"
        const val COLOR_MODE_FIXED = "fixed"
        const val COLOR_MODE_PALETTE = "palette"
        const val COLOR_MODE_THEME = "theme"

        const val PALETTE_VIVID_PAINT = "vivid_paint"
        const val PALETTE_NEON_GRAFFITI = "neon_graffiti"
        const val PALETTE_SOFT_PASTEL = "soft_pastel"
        const val PALETTE_SUMIRE = "sumire"
        const val PALETTE_MONOCHROME_INK = "monochrome_ink"

        @ColorInt
        const val DEFAULT_PAINT_COLOR: Int = 0xFF111111.toInt()

        val Disabled = SprayPaintSettings(
            enabled = false,
            colorMode = COLOR_MODE_RANDOM,
            fixedColor = DEFAULT_PAINT_COLOR,
            palette = PALETTE_VIVID_PAINT,
            quality = KeyboardTouchEffectQuality.HIGH
        )

        fun normalizeColorMode(value: String?): String {
            return when (value) {
                COLOR_MODE_FIXED -> COLOR_MODE_FIXED
                COLOR_MODE_PALETTE -> COLOR_MODE_PALETTE
                COLOR_MODE_THEME -> COLOR_MODE_THEME
                else -> COLOR_MODE_RANDOM
            }
        }

        fun normalizePalette(value: String?): String {
            return when (value) {
                PALETTE_NEON_GRAFFITI -> PALETTE_NEON_GRAFFITI
                PALETTE_SOFT_PASTEL -> PALETTE_SOFT_PASTEL
                PALETTE_SUMIRE -> PALETTE_SUMIRE
                PALETTE_MONOCHROME_INK -> PALETTE_MONOCHROME_INK
                else -> PALETTE_VIVID_PAINT
            }
        }

        @ColorInt
        fun withoutTransparentAlpha(@ColorInt color: Int): Int {
            val alpha = Color.alpha(color)
            return if (alpha == 0) {
                Color.rgb(Color.red(color), Color.green(color), Color.blue(color))
            } else {
                color
            }
        }
    }
}

internal data class SprayPaintColor(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float
) {
    companion object {
        fun fromColorInt(@ColorInt color: Int): SprayPaintColor {
            return SprayPaintColor(
                red = Color.red(color) / 255f,
                green = Color.green(color) / 255f,
                blue = Color.blue(color) / 255f,
                alpha = (Color.alpha(color) / 255f).coerceIn(0.38f, 1f)
            )
        }
    }
}

internal enum class SprayPaintEmissionKind {
    Down,
    Move,
    Up
}

internal data class SprayPaintActivePointer(
    val pointerId: Int,
    val x: Float,
    val y: Float,
    val color: SprayPaintColor,
    val downTimeMillis: Long,
    val lastEventTimeMillis: Long,
    val stationarySinceMillis: Long
) {
    fun activeDurationMillis(nowMillis: Long): Long =
        (nowMillis - downTimeMillis).coerceAtLeast(0L)

    fun stationaryDurationMillis(nowMillis: Long): Long =
        (nowMillis - stationarySinceMillis).coerceAtLeast(0L)
}

internal sealed class SprayPaintInputCommand {
    abstract val eventTimeMillis: Long

    data class Spray(
        val pointerId: Int,
        val x: Float,
        val y: Float,
        val previousX: Float,
        val previousY: Float,
        val velocityX: Float,
        val velocityY: Float,
        val color: SprayPaintColor,
        val kind: SprayPaintEmissionKind,
        override val eventTimeMillis: Long
    ) : SprayPaintInputCommand()

    data class PointerUp(
        val pointerId: Int,
        override val eventTimeMillis: Long
    ) : SprayPaintInputCommand()

    data class PointerCancel(
        val pointerId: Int,
        override val eventTimeMillis: Long
    ) : SprayPaintInputCommand()

    data class CancelAll(
        override val eventTimeMillis: Long
    ) : SprayPaintInputCommand()
}
