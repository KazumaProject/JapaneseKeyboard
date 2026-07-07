package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.graphics.Color
import android.os.SystemClock
import androidx.annotation.ColorInt
import java.util.Random
import kotlin.math.sqrt

internal class FluidInputInjector(
    private val queue: FluidInputCommandQueue,
    private val clock: () -> Long = { SystemClock.uptimeMillis() },
    private val random: Random = Random()
) {
    private data class PointerState(
        val color: FluidInkColor,
        var lastX: Float,
        var lastY: Float,
        var lastEventTimeMillis: Long
    )

    private val pointerStates = HashMap<Int, PointerState>()
    private var settings = FluidInkSettings.Disabled

    fun configure(
        colorMode: String,
        @ColorInt fixedColor: Int
    ) {
        settings = FluidInkSettings(
            enabled = true,
            colorMode = colorMode,
            fixedColor = FluidInkSettings.withoutTransparentAlpha(fixedColor)
        )
    }

    fun disable() {
        settings = FluidInkSettings.Disabled
        clearActivePointers()
        queue.clear()
    }

    fun onPointerDown(pointerId: Int, x: Float, y: Float): Boolean {
        if (!settings.enabled) return false
        val now = clock()
        val color = resolveColor()
        pointerStates[pointerId] = PointerState(
            color = color,
            lastX = x,
            lastY = y,
            lastEventTimeMillis = now
        )
        return queue.offer(
            FluidInputCommand.Splat(
                pointerId = pointerId,
                x = x,
                y = y,
                velocityX = 0f,
                velocityY = 0f,
                color = color,
                radiusPx = DOWN_SPLAT_RADIUS_PX,
                kind = FluidSplatKind.Down,
                eventTimeMillis = now,
                injectVelocity = false,
                injectDye = true,
                canReplaceQueuedMove = false
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
        if (distance < MOVE_DISTANCE_EPSILON_PX) {
            return false
        }

        val dtMillis = (now - state.lastEventTimeMillis).coerceAtLeast(1L)
        state.lastX = x
        state.lastY = y
        state.lastEventTimeMillis = now

        val dyeQueued = queue.offer(
            FluidInputCommand.Splat(
                pointerId = pointerId,
                x = x,
                y = y,
                velocityX = 0f,
                velocityY = 0f,
                color = state.color,
                radiusPx = MOVE_SPLAT_RADIUS_PX,
                kind = FluidSplatKind.Move,
                eventTimeMillis = now,
                injectVelocity = false,
                injectDye = true,
                canReplaceQueuedMove = false
            )
        )
        val velocityQueued = queue.offer(
            FluidInputCommand.Splat(
                pointerId = pointerId,
                x = x,
                y = y,
                velocityX = dx / dtMillis,
                velocityY = dy / dtMillis,
                color = state.color,
                radiusPx = MOVE_SPLAT_RADIUS_PX,
                kind = FluidSplatKind.Move,
                eventTimeMillis = now,
                injectVelocity = true,
                injectDye = false,
                canReplaceQueuedMove = true
            )
        )
        return dyeQueued || velocityQueued
    }

    fun onPointerUp(pointerId: Int): Boolean {
        val state = pointerStates.remove(pointerId) ?: return false
        return queue.offer(
            FluidInputCommand.PointerUp(
                pointerId = pointerId,
                eventTimeMillis = clock().coerceAtLeast(state.lastEventTimeMillis)
            )
        )
    }

    fun onPointerCancel(pointerId: Int): Boolean {
        val state = pointerStates.remove(pointerId) ?: return false
        return queue.offer(
            FluidInputCommand.PointerCancel(
                pointerId = pointerId,
                eventTimeMillis = clock().coerceAtLeast(state.lastEventTimeMillis)
            )
        )
    }

    fun onCancel(): Boolean {
        if (pointerStates.isEmpty()) return false
        pointerStates.clear()
        return queue.offer(FluidInputCommand.CancelAll(eventTimeMillis = clock()))
    }

    fun clearActivePointers() {
        pointerStates.clear()
    }

    fun pointerStateCountForTesting(): Int = pointerStates.size

    private fun resolveColor(): FluidInkColor {
        val color = if (settings.normalizedColorMode == FluidInkSettings.COLOR_MODE_FIXED) {
            settings.fixedColor
        } else {
            randomInkColor()
        }
        return FluidInkColor.fromColorInt(color)
    }

    @ColorInt
    private fun randomInkColor(): Int {
        val baseColor = CALM_WA_INK_PALETTE[random.nextInt(CALM_WA_INK_PALETTE.size)]
        val shade = 0.96f + random.nextFloat() * 0.18f
        val alpha = 198 + random.nextInt(39)
        return Color.argb(
            alpha,
            (Color.red(baseColor) * shade).toInt().coerceIn(0, 255),
            (Color.green(baseColor) * shade).toInt().coerceIn(0, 255),
            (Color.blue(baseColor) * shade).toInt().coerceIn(0, 255)
        )
    }

    companion object {
        private const val MOVE_DISTANCE_EPSILON_PX = 0.75f
        private const val DOWN_SPLAT_RADIUS_PX = 38f
        private const val MOVE_SPLAT_RADIUS_PX = 26f
        private val CALM_WA_INK_PALETTE = intArrayOf(
            Color.rgb(30, 31, 28),    // sumi
            Color.rgb(38, 58, 96),    // ai
            Color.rgb(112, 55, 42),   // muted shu
            Color.rgb(66, 92, 68),    // matsuba
            Color.rgb(82, 88, 78),    // nezumi
            Color.rgb(86, 62, 88)     // subdued murasaki
        )
    }
}
