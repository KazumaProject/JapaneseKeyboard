package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.graphics.Color
import androidx.annotation.ColorInt
import java.util.Random
import kotlin.math.max
import kotlin.math.min

internal data class CinematicWaveSettings(
    val enabled: Boolean,
    val colorMode: String,
    @ColorInt val primaryColor: Int,
    @ColorInt val secondaryColor: Int,
    val secondaryColorAuto: Boolean,
    val waveType: String,
    val opacityPercent: Int,
    val intensityPercent: Int,
    val motion: String,
    val touchResponse: String,
    val quality: String
) {
    val normalizedColorMode: String = normalizeColorMode(colorMode)
    val normalizedWaveType: String = normalizeWaveType(waveType)
    val normalizedMotion: String = normalizeMotion(motion)
    val normalizedTouchResponse: String = normalizeTouchResponse(touchResponse)
    val normalizedQuality: String = normalizeQuality(quality)
    val opacity: Float = (opacityPercent / 100f).coerceIn(0.18f, 0.68f)
    val intensity: Float = (intensityPercent / 100f).coerceIn(0.35f, 1.8f)

    companion object {
        const val COLOR_MODE_CINEMATIC_RANDOM = "cinematic_random"
        const val COLOR_MODE_CUSTOM = "custom"

        const val WAVE_TYPE_AURORA_MEMBRANE = "aurora_membrane"
        const val WAVE_TYPE_SILK_SINE = "silk_sine"

        const val MOTION_CALM = "calm"
        const val MOTION_ELEGANT = "elegant"
        const val MOTION_DYNAMIC = "dynamic"

        const val TOUCH_RESPONSE_SUBTLE = "subtle"
        const val TOUCH_RESPONSE_NORMAL = "normal"
        const val TOUCH_RESPONSE_DEEP = "deep"

        const val QUALITY_BATTERY_SAVER = "battery_saver"
        const val QUALITY_BALANCED = "balanced"
        const val QUALITY_CINEMATIC = "cinematic"

        @ColorInt
        const val DEFAULT_PRIMARY_COLOR: Int = 0xFF41D9FF.toInt()

        @ColorInt
        const val DEFAULT_SECONDARY_COLOR: Int = 0xFF8B5CFF.toInt()

        val Disabled = CinematicWaveSettings(
            enabled = false,
            colorMode = COLOR_MODE_CINEMATIC_RANDOM,
            primaryColor = DEFAULT_PRIMARY_COLOR,
            secondaryColor = DEFAULT_SECONDARY_COLOR,
            secondaryColorAuto = true,
            waveType = WAVE_TYPE_AURORA_MEMBRANE,
            opacityPercent = 46,
            intensityPercent = 100,
            motion = MOTION_ELEGANT,
            touchResponse = TOUCH_RESPONSE_NORMAL,
            quality = QUALITY_BALANCED
        )

        fun normalizeColorMode(value: String?): String {
            return when (value) {
                COLOR_MODE_CUSTOM -> COLOR_MODE_CUSTOM
                else -> COLOR_MODE_CINEMATIC_RANDOM
            }
        }

        fun normalizeWaveType(value: String?): String {
            return when (value) {
                WAVE_TYPE_SILK_SINE -> WAVE_TYPE_SILK_SINE
                else -> WAVE_TYPE_AURORA_MEMBRANE
            }
        }

        fun normalizeMotion(value: String?): String {
            return when (value) {
                MOTION_CALM -> MOTION_CALM
                MOTION_DYNAMIC -> MOTION_DYNAMIC
                else -> MOTION_ELEGANT
            }
        }

        fun normalizeTouchResponse(value: String?): String {
            return when (value) {
                TOUCH_RESPONSE_SUBTLE -> TOUCH_RESPONSE_SUBTLE
                TOUCH_RESPONSE_DEEP -> TOUCH_RESPONSE_DEEP
                else -> TOUCH_RESPONSE_NORMAL
            }
        }

        fun normalizeQuality(value: String?): String {
            return when (value) {
                QUALITY_BATTERY_SAVER -> QUALITY_BATTERY_SAVER
                QUALITY_CINEMATIC -> QUALITY_CINEMATIC
                else -> QUALITY_BALANCED
            }
        }

        @ColorInt
        fun withoutTransparentAlpha(@ColorInt color: Int): Int {
            return if (Color.alpha(color) == 0) {
                0xFF000000.toInt() or (color and 0x00FFFFFF)
            } else {
                color
            }
        }
    }
}

internal data class CinematicWaveColor(
    val red: Float,
    val green: Float,
    val blue: Float
) {
    fun mix(other: CinematicWaveColor, amount: Float): CinematicWaveColor {
        val t = amount.coerceIn(0f, 1f)
        return CinematicWaveColor(
            red = red + (other.red - red) * t,
            green = green + (other.green - green) * t,
            blue = blue + (other.blue - blue) * t
        )
    }

    fun scale(amount: Float): CinematicWaveColor {
        return CinematicWaveColor(
            red = (red * amount).coerceIn(0f, 1f),
            green = (green * amount).coerceIn(0f, 1f),
            blue = (blue * amount).coerceIn(0f, 1f)
        )
    }

    companion object {
        val NearBlack = CinematicWaveColor(0.018f, 0.024f, 0.036f)
        val SoftWhite = CinematicWaveColor(0.86f, 0.94f, 1f)

        fun fromColorInt(@ColorInt color: Int): CinematicWaveColor {
            val normalized = CinematicWaveSettings.withoutTransparentAlpha(color)
            return CinematicWaveColor(
                red = Color.red(normalized) / 255f,
                green = Color.green(normalized) / 255f,
                blue = Color.blue(normalized) / 255f
            )
        }
    }
}

internal data class CinematicWavePalette(
    val name: String,
    val base: CinematicWaveColor,
    val primary: CinematicWaveColor,
    val secondary: CinematicWaveColor,
    val highlight: CinematicWaveColor
) {
    fun mix(other: CinematicWavePalette, amount: Float): CinematicWavePalette {
        return CinematicWavePalette(
            name = "$name/${other.name}",
            base = base.mix(other.base, amount),
            primary = primary.mix(other.primary, amount),
            secondary = secondary.mix(other.secondary, amount),
            highlight = highlight.mix(other.highlight, amount)
        )
    }
}

internal class CinematicWaveColorController(
    private val random: Random = Random()
) {
    private var settings = CinematicWaveSettings.Disabled
    private var currentPalette: CinematicWavePalette = PRESET_PALETTES[0]
    private var nextPalette: CinematicWavePalette = PRESET_PALETTES[1]
    private var transitionStartMs = 0L
    private var transitionDurationMs = DEFAULT_TRANSITION_MS

    fun configure(settings: CinematicWaveSettings, nowMs: Long) {
        val previousMode = this.settings.normalizedColorMode
        val nextSettings = settings.copy(
            primaryColor = CinematicWaveSettings.withoutTransparentAlpha(settings.primaryColor),
            secondaryColor = CinematicWaveSettings.withoutTransparentAlpha(settings.secondaryColor)
        )
        this.settings = nextSettings

        if (nextSettings.normalizedColorMode == CinematicWaveSettings.COLOR_MODE_CUSTOM) {
            val customPalette = customPaletteFromSettings(nextSettings)
            currentPalette = customPalette
            nextPalette = customPalette
            transitionStartMs = nowMs
            transitionDurationMs = DEFAULT_TRANSITION_MS
            return
        }

        if (previousMode != CinematicWaveSettings.COLOR_MODE_CINEMATIC_RANDOM ||
            currentPalette.name == nextPalette.name
        ) {
            currentPalette = choosePreset(excludingName = null)
            nextPalette = choosePreset(excludingName = currentPalette.name)
            transitionStartMs = nowMs
            transitionDurationMs = nextTransitionDurationMs()
        }
    }

    fun paletteAt(nowMs: Long): CinematicWavePalette {
        if (settings.normalizedColorMode == CinematicWaveSettings.COLOR_MODE_CUSTOM) {
            return currentPalette
        }
        val duration = max(1L, transitionDurationMs)
        val t = ((nowMs - transitionStartMs).toFloat() / duration).coerceIn(0f, 1f)
        if (t >= 1f) {
            currentPalette = nextPalette
            nextPalette = choosePreset(excludingName = currentPalette.name)
            transitionStartMs = nowMs
            transitionDurationMs = nextTransitionDurationMs()
            return currentPalette
        }
        val smoothed = t * t * (3f - 2f * t)
        return currentPalette.mix(nextPalette, smoothed)
    }

    private fun choosePreset(excludingName: String?): CinematicWavePalette {
        val candidates = PRESET_PALETTES.filterNot { it.name == excludingName }
        return candidates[random.nextInt(candidates.size)]
    }

    private fun nextTransitionDurationMs(): Long {
        return MIN_TRANSITION_MS + random.nextInt((MAX_TRANSITION_MS - MIN_TRANSITION_MS).toInt())
    }

    private fun customPaletteFromSettings(settings: CinematicWaveSettings): CinematicWavePalette {
        val primary = CinematicWaveColor.fromColorInt(settings.primaryColor)
        val secondary = if (settings.secondaryColorAuto) {
            deriveSecondary(primary)
        } else {
            CinematicWaveColor.fromColorInt(settings.secondaryColor)
        }
        val base = deriveBase(primary, secondary)
        val highlight = primary.mix(CinematicWaveColor.SoftWhite, 0.72f)
        return CinematicWavePalette(
            name = "Custom",
            base = base,
            primary = primary.mix(CinematicWaveColor.SoftWhite, 0.08f),
            secondary = secondary.mix(base, 0.12f),
            highlight = highlight
        )
    }

    private fun deriveBase(
        primary: CinematicWaveColor,
        secondary: CinematicWaveColor
    ): CinematicWaveColor {
        return primary.mix(secondary, 0.38f)
            .mix(CinematicWaveColor.NearBlack, 0.86f)
            .scale(0.58f)
    }

    private fun deriveSecondary(primary: CinematicWaveColor): CinematicWaveColor {
        val maxChannel = max(primary.red, max(primary.green, primary.blue))
        val minChannel = min(primary.red, min(primary.green, primary.blue))
        return if (maxChannel - minChannel < 0.16f) {
            CinematicWaveColor(0.48f, 0.42f, 0.72f)
        } else {
            CinematicWaveColor(
                red = (primary.blue * 0.72f + 0.25f).coerceIn(0f, 1f),
                green = (primary.red * 0.28f + primary.green * 0.18f + 0.10f).coerceIn(0f, 1f),
                blue = (primary.green * 0.72f + 0.25f).coerceIn(0f, 1f)
            )
        }
    }

    companion object {
        private const val MIN_TRANSITION_MS = 12_000L
        private const val MAX_TRANSITION_MS = 30_000L
        private const val DEFAULT_TRANSITION_MS = 18_000L

        val PRESET_PALETTES = listOf(
            CinematicWavePalette(
                name = "Midnight Aurora",
                base = CinematicWaveColor(0.012f, 0.018f, 0.050f),
                primary = CinematicWaveColor(0.20f, 0.88f, 1.00f),
                secondary = CinematicWaveColor(0.52f, 0.28f, 0.92f),
                highlight = CinematicWaveColor(0.78f, 0.92f, 1.00f)
            ),
            CinematicWavePalette(
                name = "Deep Ocean",
                base = CinematicWaveColor(0.000f, 0.040f, 0.070f),
                primary = CinematicWaveColor(0.16f, 0.86f, 0.86f),
                secondary = CinematicWaveColor(0.10f, 0.22f, 0.76f),
                highlight = CinematicWaveColor(0.70f, 1.00f, 0.96f)
            ),
            CinematicWavePalette(
                name = "Purple Nebula",
                base = CinematicWaveColor(0.035f, 0.010f, 0.060f),
                primary = CinematicWaveColor(0.86f, 0.22f, 0.82f),
                secondary = CinematicWaveColor(0.30f, 0.24f, 0.92f),
                highlight = CinematicWaveColor(0.88f, 0.74f, 1.00f)
            ),
            CinematicWavePalette(
                name = "Golden Dusk",
                base = CinematicWaveColor(0.055f, 0.032f, 0.018f),
                primary = CinematicWaveColor(1.00f, 0.62f, 0.20f),
                secondary = CinematicWaveColor(0.90f, 0.42f, 0.46f),
                highlight = CinematicWaveColor(1.00f, 0.90f, 0.72f)
            ),
            CinematicWavePalette(
                name = "Silver Mist",
                base = CinematicWaveColor(0.035f, 0.040f, 0.048f),
                primary = CinematicWaveColor(0.58f, 0.74f, 0.90f),
                secondary = CinematicWaveColor(0.42f, 0.34f, 0.56f),
                highlight = CinematicWaveColor(0.90f, 0.94f, 0.96f)
            )
        )
    }
}
