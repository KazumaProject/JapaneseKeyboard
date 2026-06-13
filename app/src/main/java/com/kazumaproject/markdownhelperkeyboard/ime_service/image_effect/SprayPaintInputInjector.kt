package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.graphics.Color
import android.os.SystemClock
import androidx.annotation.ColorInt
import java.util.Random
import kotlin.math.sqrt

internal class SprayPaintInputInjector(
    private val queue: SprayPaintInputCommandQueue,
    private val clock: () -> Long = { SystemClock.uptimeMillis() },
    private val random: Random = Random()
) {
    private data class PointerState(
        val color: SprayPaintColor,
        val style: SprayPaintPaletteStyle,
        val downTimeMillis: Long,
        var lastX: Float,
        var lastY: Float,
        var lastEventTimeMillis: Long
    )

    private data class PaintChoice(
        val color: SprayPaintColor,
        val style: SprayPaintPaletteStyle
    )

    private val pointerStates = HashMap<Int, PointerState>()
    private var settings = SprayPaintSettings.Disabled

    fun configure(settings: SprayPaintSettings) {
        this.settings = settings.copy(
            fixedColor = SprayPaintSettings.withoutTransparentAlpha(settings.fixedColor)
        )
        if (!settings.enabled) {
            clearActivePointers()
            queue.clear()
        }
    }

    fun disable() {
        settings = SprayPaintSettings.Disabled
        clearActivePointers()
        queue.clear()
    }

    fun onPointerDown(pointerId: Int, x: Float, y: Float): Boolean {
        if (!settings.enabled) return false
        val now = clock()
        val paintChoice = resolvePaintChoice()
        pointerStates[pointerId] = PointerState(
            color = paintChoice.color,
            style = paintChoice.style,
            downTimeMillis = now,
            lastX = x,
            lastY = y,
            lastEventTimeMillis = now
        )
        return queue.offer(
            SprayPaintInputCommand.Spray(
                pointerId = pointerId,
                x = x,
                y = y,
                previousX = x,
                previousY = y,
                velocityX = 0f,
                velocityY = 0f,
                color = paintChoice.color,
                style = paintChoice.style,
                kind = SprayPaintEmissionKind.Down,
                eventTimeMillis = now
            )
        )
    }

    fun onPointerMove(pointerId: Int, x: Float, y: Float): Boolean {
        if (!settings.enabled) return false
        val state = pointerStates[pointerId] ?: return false
        val now = clock()
        val dx = x - state.lastX
        val dy = y - state.lastY
        val distance = sqrt(dx * dx + dy * dy)
        if (distance < MOVE_DISTANCE_EPSILON_PX) return false

        val previousX = state.lastX
        val previousY = state.lastY
        val dtMillis = (now - state.lastEventTimeMillis).coerceAtLeast(1L)
        state.lastX = x
        state.lastY = y
        state.lastEventTimeMillis = now

        return queue.offer(
            SprayPaintInputCommand.Spray(
                pointerId = pointerId,
                x = x,
                y = y,
                previousX = previousX,
                previousY = previousY,
                velocityX = dx / dtMillis,
                velocityY = dy / dtMillis,
                color = state.color,
                style = state.style,
                kind = SprayPaintEmissionKind.Move,
                eventTimeMillis = now
            )
        )
    }

    fun onPointerUp(pointerId: Int, x: Float? = null, y: Float? = null): Boolean {
        val state = pointerStates.remove(pointerId) ?: return false
        val now = clock().coerceAtLeast(state.lastEventTimeMillis)
        var queued = false
        if (settings.enabled && x != null && y != null) {
            queued = queue.offer(
                SprayPaintInputCommand.Spray(
                    pointerId = pointerId,
                    x = x,
                    y = y,
                    previousX = state.lastX,
                    previousY = state.lastY,
                    velocityX = 0f,
                    velocityY = 0f,
                    color = state.color,
                    style = state.style,
                    kind = SprayPaintEmissionKind.Up,
                    eventTimeMillis = now
                )
            )
        }
        queued = queue.offer(
            SprayPaintInputCommand.PointerUp(
                pointerId = pointerId,
                eventTimeMillis = now
            )
        ) || queued
        return queued
    }

    fun onPointerCancel(pointerId: Int): Boolean {
        val state = pointerStates.remove(pointerId) ?: return false
        return queue.offer(
            SprayPaintInputCommand.PointerCancel(
                pointerId = pointerId,
                eventTimeMillis = clock().coerceAtLeast(state.lastEventTimeMillis)
            )
        )
    }

    fun onCancel(): Boolean {
        if (pointerStates.isEmpty()) return false
        pointerStates.clear()
        return queue.offer(SprayPaintInputCommand.CancelAll(eventTimeMillis = clock()))
    }

    fun clearActivePointers() {
        pointerStates.clear()
    }

    fun pointerStateCountForTesting(): Int = pointerStates.size

    private fun resolvePaintChoice(): PaintChoice {
        val palette = when (settings.normalizedColorMode) {
            SprayPaintSettings.COLOR_MODE_FIXED -> settings.normalizedPalette
            SprayPaintSettings.COLOR_MODE_PALETTE,
            SprayPaintSettings.COLOR_MODE_THEME -> settings.normalizedPalette
            else -> randomModePalette()
        }
        val color = when (settings.normalizedColorMode) {
            SprayPaintSettings.COLOR_MODE_FIXED -> settings.fixedColor
            else -> randomPaletteColor(palette)
        }
        return PaintChoice(
            color = SprayPaintColor.fromColorInt(color),
            style = SprayPaintSettings.styleForPalette(palette)
        )
    }

    private fun randomModePalette(): String {
        return RANDOM_MODE_PALETTES[random.nextInt(RANDOM_MODE_PALETTES.size)]
    }

    @ColorInt
    private fun randomPaletteColor(palette: String): Int {
        val colors = when (SprayPaintSettings.normalizePalette(palette)) {
            SprayPaintSettings.PALETTE_NEON_GRAFFITI -> NEON_GRAFFITI
            SprayPaintSettings.PALETTE_SOFT_PASTEL -> SOFT_PASTEL
            SprayPaintSettings.PALETTE_SUMIRE -> SUMIRE
            else -> VIVID_PAINT
        }
        val base = colors[random.nextInt(colors.size)]
        val normalizedPalette = SprayPaintSettings.normalizePalette(palette)
        val shade = when (normalizedPalette) {
            SprayPaintSettings.PALETTE_NEON_GRAFFITI -> 0.9f + random.nextFloat() * 0.3f
            SprayPaintSettings.PALETTE_SOFT_PASTEL -> 0.98f + random.nextFloat() * 0.11f
            SprayPaintSettings.PALETTE_SUMIRE -> 0.94f + random.nextFloat() * 0.16f
            else -> 0.92f + random.nextFloat() * 0.22f
        }
        val alpha = when (normalizedPalette) {
            SprayPaintSettings.PALETTE_NEON_GRAFFITI -> 224 + random.nextInt(32)
            SprayPaintSettings.PALETTE_SOFT_PASTEL -> 178 + random.nextInt(36)
            SprayPaintSettings.PALETTE_SUMIRE -> 172 + random.nextInt(44)
            else -> 214 + random.nextInt(40)
        }
        return Color.argb(
            alpha,
            (Color.red(base) * shade).toInt().coerceIn(0, 255),
            (Color.green(base) * shade).toInt().coerceIn(0, 255),
            (Color.blue(base) * shade).toInt().coerceIn(0, 255)
        )
    }

    companion object {
        private const val MOVE_DISTANCE_EPSILON_PX = 0.9f

        private val RANDOM_MODE_PALETTES = arrayOf(
            SprayPaintSettings.PALETTE_VIVID_PAINT,
            SprayPaintSettings.PALETTE_NEON_GRAFFITI,
            SprayPaintSettings.PALETTE_SOFT_PASTEL,
            SprayPaintSettings.PALETTE_SUMIRE
        )

        private val VIVID_PAINT = intArrayOf(
            Color.rgb(0, 199, 255),
            Color.rgb(255, 47, 154),
            Color.rgb(255, 214, 0),
            Color.rgb(106, 230, 32),
            Color.rgb(255, 126, 31),
            Color.rgb(134, 79, 255)
        )

        private val NEON_GRAFFITI = intArrayOf(
            Color.rgb(255, 43, 191),
            Color.rgb(0, 132, 255),
            Color.rgb(116, 255, 33),
            Color.rgb(158, 63, 255)
        )

        private val SOFT_PASTEL = intArrayOf(
            Color.rgb(255, 152, 190),
            Color.rgb(126, 232, 191),
            Color.rgb(188, 158, 255),
            Color.rgb(118, 201, 255)
        )

        private val SUMIRE = intArrayOf(
            Color.rgb(132, 94, 214),
            Color.rgb(169, 96, 198),
            Color.rgb(138, 174, 238),
            Color.rgb(246, 174, 222),
            Color.rgb(211, 196, 255)
        )
    }
}
