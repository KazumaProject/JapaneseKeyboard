package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.os.SystemClock
import kotlin.math.sqrt

internal class CinematicWaveInputInjector(
    private val queue: CinematicWaveInputCommandQueue,
    private val clock: () -> Long = { SystemClock.uptimeMillis() }
) {
    private data class PointerState(
        var lastX: Float,
        var lastY: Float,
        var lastEventTimeMs: Long
    )

    private val pointerStates = HashMap<Int, PointerState>()
    private var enabled = false

    fun configure(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) {
            clearActivePointers()
            queue.clear()
        }
    }

    fun disable() {
        enabled = false
        clearActivePointers()
        queue.clear()
    }

    fun onPointerDown(pointerId: Int, x: Float, y: Float, pressure: Float): Boolean {
        if (!enabled || pointerStates.size >= MAX_TOUCH_POINTS) return false
        val now = clock()
        pointerStates[pointerId] = PointerState(
            lastX = x,
            lastY = y,
            lastEventTimeMs = now
        )
        return queue.offer(
            CinematicWaveInputCommand.Pointer(
                pointerId = pointerId,
                x = x,
                y = y,
                pressure = pressure.coerceIn(0.35f, 1.5f),
                kind = CinematicWaveInputKind.Down,
                eventTimeMs = now
            )
        )
    }

    fun onPointerMove(pointerId: Int, x: Float, y: Float, pressure: Float): Boolean {
        if (!enabled) return false
        val state = pointerStates[pointerId] ?: return false
        val dx = x - state.lastX
        val dy = y - state.lastY
        if (sqrt(dx * dx + dy * dy) < MOVE_DISTANCE_EPSILON_PX) return false

        val now = clock().coerceAtLeast(state.lastEventTimeMs)
        state.lastX = x
        state.lastY = y
        state.lastEventTimeMs = now
        return queue.offer(
            CinematicWaveInputCommand.Pointer(
                pointerId = pointerId,
                x = x,
                y = y,
                pressure = pressure.coerceIn(0.35f, 1.5f),
                kind = CinematicWaveInputKind.Move,
                eventTimeMs = now
            )
        )
    }

    fun onPointerUp(pointerId: Int, x: Float? = null, y: Float? = null, pressure: Float = 1f): Boolean {
        val state = pointerStates.remove(pointerId) ?: return false
        val now = clock().coerceAtLeast(state.lastEventTimeMs)
        var queued = false
        if (enabled && x != null && y != null) {
            queued = queue.offer(
                CinematicWaveInputCommand.Pointer(
                    pointerId = pointerId,
                    x = x,
                    y = y,
                    pressure = pressure.coerceIn(0.35f, 1.5f),
                    kind = CinematicWaveInputKind.Up,
                    eventTimeMs = now
                )
            )
        }
        queued = queue.offer(
            CinematicWaveInputCommand.PointerUp(
                pointerId = pointerId,
                eventTimeMs = now
            )
        ) || queued
        return queued
    }

    fun onPointerCancel(pointerId: Int): Boolean {
        val state = pointerStates.remove(pointerId) ?: return false
        return queue.offer(
            CinematicWaveInputCommand.PointerCancel(
                pointerId = pointerId,
                eventTimeMs = clock().coerceAtLeast(state.lastEventTimeMs)
            )
        )
    }

    fun onCancel(): Boolean {
        if (!enabled && pointerStates.isEmpty()) return false
        pointerStates.clear()
        return queue.offer(CinematicWaveInputCommand.CancelAll(eventTimeMs = clock()))
    }

    fun clearActivePointers() {
        pointerStates.clear()
    }

    fun pointerStateCountForTesting(): Int = pointerStates.size

    companion object {
        const val MAX_TOUCH_POINTS = 5
        private const val MOVE_DISTANCE_EPSILON_PX = 0.7f
    }
}
