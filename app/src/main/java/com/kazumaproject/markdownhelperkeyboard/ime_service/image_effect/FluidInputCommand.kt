package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.graphics.Color
import androidx.annotation.ColorInt

internal data class FluidInkSettings(
    val enabled: Boolean,
    val colorMode: String,
    @ColorInt val fixedColor: Int,
    val quality: String = KeyboardTouchEffectQuality.HIGH
) {
    val normalizedColorMode: String =
        if (colorMode == COLOR_MODE_FIXED) COLOR_MODE_FIXED else COLOR_MODE_RANDOM
    val normalizedQuality: String = KeyboardTouchEffectQuality.normalize(quality)

    companion object {
        const val COLOR_MODE_RANDOM = "random"
        const val COLOR_MODE_FIXED = "fixed"

        @ColorInt
        const val DEFAULT_INK_COLOR: Int = 0xFF111111.toInt()

        val Disabled = FluidInkSettings(
            enabled = false,
            colorMode = COLOR_MODE_RANDOM,
            fixedColor = DEFAULT_INK_COLOR,
            quality = KeyboardTouchEffectQuality.HIGH
        )

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

internal data class FluidInkColor(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float
) {
    companion object {
        fun fromColorInt(@ColorInt color: Int): FluidInkColor {
            val alpha = (Color.alpha(color) / 255f).coerceIn(0.35f, 1f)
            return FluidInkColor(
                red = Color.red(color) / 255f,
                green = Color.green(color) / 255f,
                blue = Color.blue(color) / 255f,
                alpha = alpha
            )
        }
    }
}

internal enum class FluidSplatKind {
    Down,
    Move
}

internal sealed class FluidInputCommand {
    abstract val eventTimeMillis: Long

    data class Splat(
        val pointerId: Int,
        val x: Float,
        val y: Float,
        val velocityX: Float,
        val velocityY: Float,
        val color: FluidInkColor,
        val radiusPx: Float,
        val kind: FluidSplatKind,
        override val eventTimeMillis: Long,
        val injectVelocity: Boolean = true,
        val injectDye: Boolean = true,
        val canReplaceQueuedMove: Boolean = kind == FluidSplatKind.Move && injectVelocity && !injectDye
    ) : FluidInputCommand()

    data class PointerUp(
        val pointerId: Int,
        override val eventTimeMillis: Long
    ) : FluidInputCommand()

    data class PointerCancel(
        val pointerId: Int,
        override val eventTimeMillis: Long
    ) : FluidInputCommand()

    data class CancelAll(
        override val eventTimeMillis: Long
    ) : FluidInputCommand()
}
