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

        const val PALETTE_SPRAY = "spray"
        const val PALETTE_PAINT_SPLASH = "paint_splash"
        const val PALETTE_GRAFFITI = "graffiti"
        const val PALETTE_LIQUID_PAINT = "liquid_paint"
        const val PALETTE_FLOWER_PETALS = "flower_petals"

        private const val LEGACY_PALETTE_VIVID_PAINT = "vivid_paint"
        private const val LEGACY_PALETTE_NEON_GRAFFITI = "neon_graffiti"
        private const val LEGACY_PALETTE_SOFT_PASTEL = "soft_pastel"
        private const val LEGACY_PALETTE_SUMIRE = "sumire"

        @ColorInt
        const val DEFAULT_PAINT_COLOR: Int = 0xFF111111.toInt()

        val Disabled = SprayPaintSettings(
            enabled = false,
            colorMode = COLOR_MODE_RANDOM,
            fixedColor = DEFAULT_PAINT_COLOR,
            palette = PALETTE_PAINT_SPLASH,
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
                PALETTE_SPRAY -> PALETTE_SPRAY
                PALETTE_PAINT_SPLASH -> PALETTE_PAINT_SPLASH
                PALETTE_GRAFFITI -> PALETTE_GRAFFITI
                PALETTE_LIQUID_PAINT -> PALETTE_LIQUID_PAINT
                PALETTE_FLOWER_PETALS -> PALETTE_FLOWER_PETALS
                LEGACY_PALETTE_VIVID_PAINT -> PALETTE_PAINT_SPLASH
                LEGACY_PALETTE_NEON_GRAFFITI -> PALETTE_GRAFFITI
                LEGACY_PALETTE_SOFT_PASTEL -> PALETTE_SPRAY
                LEGACY_PALETTE_SUMIRE -> PALETTE_FLOWER_PETALS
                else -> PALETTE_PAINT_SPLASH
            }
        }

        fun styleForPalette(value: String?): SprayPaintPaletteStyle {
            return when (normalizePalette(value)) {
                PALETTE_SPRAY -> SprayPaintPaletteStyle.Spray
                PALETTE_GRAFFITI -> SprayPaintPaletteStyle.Graffiti
                PALETTE_LIQUID_PAINT -> SprayPaintPaletteStyle.LiquidPaint
                PALETTE_FLOWER_PETALS -> SprayPaintPaletteStyle.FlowerPetals
                else -> SprayPaintPaletteStyle.PaintSplash
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

internal enum class SprayPaintPaletteStyle {
    Spray,
    PaintSplash,
    Graffiti,
    LiquidPaint,
    FlowerPetals
}

internal enum class SprayPaintDepositionModel {
    AerosolGaussian,
    NoisyPaintSplat,
    DirectionalGraffiti,
    ViscousLiquid,
    PetalPrimitive
}

internal enum class SprayPaintCompositeModel {
    AdditiveMist,
    AlphaPaint,
    WetHeightPaint,
    PetalAlpha
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
    val style: SprayPaintPaletteStyle,
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
        val style: SprayPaintPaletteStyle,
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
