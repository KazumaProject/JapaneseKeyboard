package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor

import android.view.KeyEvent
import kotlin.math.abs

internal enum class CursorDirection(val keyCode: Int) {
    LEFT(KeyEvent.KEYCODE_DPAD_LEFT), RIGHT(KeyEvent.KEYCODE_DPAD_RIGHT),
    UP(KeyEvent.KEYCODE_DPAD_UP), DOWN(KeyEvent.KEYCODE_DPAD_DOWN);
    val horizontal get() = this == LEFT || this == RIGHT
    val delta get() = if (this == LEFT || this == UP) -1 else 1
}

/** Preserves sub-step distance; a fast swipe emits every completed step on its dominant axis. */
internal class CursorTrackpadGesture(private val threshold: Float, private val move: (CursorDirection) -> Unit) {
    private var x = 0f
    private var y = 0f
    fun begin(x: Float, y: Float) { this.x = x; this.y = y }
    fun move(x: Float, y: Float) {
        val dx = x - this.x
        val dy = y - this.y
        val horizontal = abs(dx) >= abs(dy)
        val distance = if (horizontal) dx else dy
        val steps = (abs(distance) / threshold).toInt()
        if (steps == 0) return
        val direction = if (horizontal) {
            if (distance < 0) CursorDirection.LEFT else CursorDirection.RIGHT
        } else {
            if (distance < 0) CursorDirection.UP else CursorDirection.DOWN
        }
        repeat(steps) { move(direction) }
        val consumed = steps * threshold * if (distance < 0) -1 else 1
        if (horizontal) { this.x += consumed; this.y = y }
        else { this.y += consumed; this.x = x }
    }
}

internal enum class CursorOperation { EDITOR, PREEDIT, CANDIDATE, SEGMENT, NONE }
internal fun resolveCursorOperation(direction: CursorDirection, hasPreedit: Boolean, converting: Boolean,
                                    segmentConversion: Boolean): CursorOperation = when {
    segmentConversion -> if (direction.horizontal) CursorOperation.SEGMENT else CursorOperation.CANDIDATE
    converting -> CursorOperation.CANDIDATE
    hasPreedit -> if (direction.horizontal) CursorOperation.PREEDIT else CursorOperation.NONE
    else -> CursorOperation.EDITOR
}
