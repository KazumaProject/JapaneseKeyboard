package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.os.SystemClock
import kotlin.math.sqrt

internal class LiquidRippleInputInjector(
    private val queue: LiquidRippleInputCommandQueue,
    private val clock: () -> Long = { SystemClock.uptimeMillis() }
) {
    private data class PointerState(
        var lastX: Float,
        var lastY: Float,
        var lastEventTimeMillis: Long
    )

    private val pointerStates = HashMap<Int, PointerState>()
    private var settings = LiquidRippleSettings.Disabled

    fun configure(enabled: Boolean) {
        settings = LiquidRippleSettings(enabled = enabled)
        if (!enabled) {
            clearActivePointers()
            queue.clear()
        }
    }

    fun disable() {
        settings = LiquidRippleSettings.Disabled
        clearActivePointers()
        queue.clear()
    }

    fun onPointerDown(pointerId: Int, x: Float, y: Float): Boolean {
        if (!settings.enabled) return false
        val now = clock()
        pointerStates[pointerId] = PointerState(
            lastX = x,
            lastY = y,
            lastEventTimeMillis = now
        )
        return queue.offer(
            LiquidRippleInputCommand.Impulse(
                pointerId = pointerId,
                x = x,
                y = y,
                strength = DOWN_IMPULSE_STRENGTH,
                radiusPx = DOWN_RADIUS_PX,
                kind = LiquidRippleImpulseKind.Down,
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

        val dtMillis = (now - state.lastEventTimeMillis).coerceAtLeast(1L)
        state.lastX = x
        state.lastY = y
        state.lastEventTimeMillis = now

        val speed = (distance / dtMillis).coerceIn(0f, MAX_MOVE_SPEED_PX_PER_MS)
        val strength = (MOVE_IMPULSE_BASE + speed * MOVE_IMPULSE_SPEED_GAIN)
            .coerceIn(MOVE_IMPULSE_MIN, MOVE_IMPULSE_MAX)
        return queue.offer(
            LiquidRippleInputCommand.Impulse(
                pointerId = pointerId,
                x = x,
                y = y,
                strength = strength,
                radiusPx = MOVE_RADIUS_PX,
                kind = LiquidRippleImpulseKind.Move,
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
                LiquidRippleInputCommand.Impulse(
                    pointerId = pointerId,
                    x = x,
                    y = y,
                    strength = UP_IMPULSE_STRENGTH,
                    radiusPx = UP_RADIUS_PX,
                    kind = LiquidRippleImpulseKind.Up,
                    eventTimeMillis = now
                )
            )
        }
        queued = queue.offer(
            LiquidRippleInputCommand.PointerUp(
                pointerId = pointerId,
                eventTimeMillis = now
            )
        ) || queued
        return queued
    }

    fun onPointerCancel(pointerId: Int): Boolean {
        val state = pointerStates.remove(pointerId) ?: return false
        return queue.offer(
            LiquidRippleInputCommand.PointerCancel(
                pointerId = pointerId,
                eventTimeMillis = clock().coerceAtLeast(state.lastEventTimeMillis)
            )
        )
    }

    fun onCancel(): Boolean {
        if (pointerStates.isEmpty()) return false
        pointerStates.clear()
        return queue.offer(LiquidRippleInputCommand.CancelAll(eventTimeMillis = clock()))
    }

    fun clearActivePointers() {
        pointerStates.clear()
    }

    fun pointerStateCountForTesting(): Int = pointerStates.size

    companion object {
        private const val MOVE_DISTANCE_EPSILON_PX = 1.1f
        private const val MAX_MOVE_SPEED_PX_PER_MS = 2.2f
        private const val DOWN_IMPULSE_STRENGTH = 0.48f
        private const val MOVE_IMPULSE_BASE = 0.08f
        private const val MOVE_IMPULSE_SPEED_GAIN = 0.045f
        private const val MOVE_IMPULSE_MIN = 0.08f
        private const val MOVE_IMPULSE_MAX = 0.16f
        private const val UP_IMPULSE_STRENGTH = 0.07f
        private const val DOWN_RADIUS_PX = 44f
        private const val MOVE_RADIUS_PX = 30f
        private const val UP_RADIUS_PX = 34f
    }
}
