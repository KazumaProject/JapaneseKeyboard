package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.graphics.Color
import android.os.SystemClock
import androidx.annotation.ColorInt
import java.util.Random
import kotlin.math.sqrt

internal class LuminousBlobInputInjector(
    private val queue: LuminousBlobInputCommandQueue,
    private val clock: () -> Long = { SystemClock.uptimeMillis() },
    private val random: Random = Random()
) {
    private data class PointerState(
        val colorSet: LuminousBlobColorSet,
        val downTimeMillis: Long,
        var lastX: Float,
        var lastY: Float,
        var lastEventTimeMillis: Long
    )

    private val pointerStates = HashMap<Int, PointerState>()
    private var settings = LuminousBlobSettings.Disabled

    fun configure(settings: LuminousBlobSettings) {
        this.settings = settings.copy(
            fixedColor = LuminousBlobSettings.withoutTransparentAlpha(settings.fixedColor)
        )
        if (!settings.enabled) {
            clearActivePointers()
            queue.clear()
        }
    }

    fun disable() {
        settings = LuminousBlobSettings.Disabled
        clearActivePointers()
        queue.clear()
    }

    fun onPointerDown(pointerId: Int, x: Float, y: Float): Boolean {
        if (!settings.enabled) return false
        val now = clock()
        val colorSet = resolveColorSet()
        pointerStates[pointerId] = PointerState(
            colorSet = colorSet,
            downTimeMillis = now,
            lastX = x,
            lastY = y,
            lastEventTimeMillis = now
        )
        return queue.offer(
            LuminousBlobInputCommand.Pointer(
                pointerId = pointerId,
                x = x,
                y = y,
                previousX = x,
                previousY = y,
                velocityX = 0f,
                velocityY = 0f,
                colorSet = colorSet,
                kind = LuminousBlobInputKind.Down,
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
            LuminousBlobInputCommand.Pointer(
                pointerId = pointerId,
                x = x,
                y = y,
                previousX = previousX,
                previousY = previousY,
                velocityX = (dx / dtMillis).coerceIn(-MAX_SPEED_PX_PER_MS, MAX_SPEED_PX_PER_MS),
                velocityY = (dy / dtMillis).coerceIn(-MAX_SPEED_PX_PER_MS, MAX_SPEED_PX_PER_MS),
                colorSet = state.colorSet,
                kind = LuminousBlobInputKind.Move,
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
                LuminousBlobInputCommand.Pointer(
                    pointerId = pointerId,
                    x = x,
                    y = y,
                    previousX = state.lastX,
                    previousY = state.lastY,
                    velocityX = 0f,
                    velocityY = 0f,
                    colorSet = state.colorSet,
                    kind = LuminousBlobInputKind.Up,
                    eventTimeMillis = now
                )
            )
        }
        queued = queue.offer(
            LuminousBlobInputCommand.PointerUp(
                pointerId = pointerId,
                eventTimeMillis = now
            )
        ) || queued
        return queued
    }

    fun onPointerCancel(pointerId: Int): Boolean {
        val state = pointerStates.remove(pointerId) ?: return false
        return queue.offer(
            LuminousBlobInputCommand.PointerCancel(
                pointerId = pointerId,
                eventTimeMillis = clock().coerceAtLeast(state.lastEventTimeMillis)
            )
        )
    }

    fun onCancel(): Boolean {
        if (!settings.enabled && pointerStates.isEmpty()) return false
        pointerStates.clear()
        return queue.offer(LuminousBlobInputCommand.CancelAll(eventTimeMillis = clock()))
    }

    fun clearActivePointers() {
        pointerStates.clear()
    }

    fun pointerStateCountForTesting(): Int = pointerStates.size

    private fun resolveColorSet(): LuminousBlobColorSet {
        val color = when (settings.normalizedColorMode) {
            LuminousBlobSettings.COLOR_MODE_FIXED,
            LuminousBlobSettings.COLOR_MODE_THEME -> settings.fixedColor

            else -> randomGoldColor()
        }
        return LuminousBlobColorSet.fromBase(LuminousBlobColor.fromColorInt(color))
    }

    @ColorInt
    private fun randomGoldColor(): Int {
        val base = GOLD_COLORS[random.nextInt(GOLD_COLORS.size)]
        val shade = 0.94f + random.nextFloat() * 0.16f
        return Color.argb(
            255,
            (Color.red(base) * shade).toInt().coerceIn(0, 255),
            (Color.green(base) * shade).toInt().coerceIn(0, 255),
            (Color.blue(base) * shade).toInt().coerceIn(0, 255)
        )
    }

    companion object {
        private const val MOVE_DISTANCE_EPSILON_PX = 0.7f
        private const val MAX_SPEED_PX_PER_MS = 3.2f

        private val GOLD_COLORS = intArrayOf(
            Color.rgb(255, 243, 106),
            Color.rgb(255, 232, 72),
            Color.rgb(255, 214, 41),
            Color.rgb(255, 248, 156),
            Color.rgb(230, 205, 18)
        )
    }
}
