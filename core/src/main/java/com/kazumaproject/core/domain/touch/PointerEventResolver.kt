package com.kazumaproject.core.domain.touch

import android.os.Build
import android.view.MotionEvent

/**
 * Stable information about one pointer in a MotionEvent.
 *
 * [pointerId] is stable for the lifetime of a touch stream. [pointerIndex] is only the index
 * used to read this particular MotionEvent and must never be persisted as pointer identity.
 */
data class PointerSample(
    val pointerId: Int,
    val pointerIndex: Int,
    val screenX: Float,
    val screenY: Float,
)

/**
 * Centralizes the Android pointer-id/index and display-coordinate rules used by keyboard views.
 */
object PointerEventResolver {
    fun at(event: MotionEvent, pointerIndex: Int): PointerSample? {
        if (pointerIndex !in 0 until event.pointerCount) return null
        return PointerSample(
            pointerId = event.getPointerId(pointerIndex),
            pointerIndex = pointerIndex,
            screenX = screenX(event, pointerIndex),
            screenY = screenY(event, pointerIndex),
        )
    }

    fun forId(event: MotionEvent, pointerId: Int): PointerSample? {
        val pointerIndex = event.findPointerIndex(pointerId)
        return at(event, pointerIndex)
    }

    fun actionPointer(event: MotionEvent): PointerSample? =
        at(event, event.actionIndex)

    /** Compact DEBUG/test representation that keeps IDs, indices, and screen coordinates together. */
    fun describe(event: MotionEvent): String =
        (0 until event.pointerCount).joinToString("|") { pointerIndex ->
            val pointer = at(event, pointerIndex)
            if (pointer == null) {
                "index=$pointerIndex:unavailable"
            } else {
                "${pointer.pointerId}@${pointer.screenX},${pointer.screenY}"
            }
        }

    fun screenX(event: MotionEvent, pointerIndex: Int): Float {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            event.getRawX(pointerIndex)
        } else {
            event.getX(pointerIndex) + event.rawX - event.x
        }
    }

    fun screenY(event: MotionEvent, pointerIndex: Int): Float {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            event.getRawY(pointerIndex)
        } else {
            event.getY(pointerIndex) + event.rawY - event.y
        }
    }

    fun historicalScreenX(
        event: MotionEvent,
        pointerIndex: Int,
        historyIndex: Int,
    ): Float {
        return event.getHistoricalX(pointerIndex, historyIndex) + event.rawX - event.x
    }

    fun historicalScreenY(
        event: MotionEvent,
        pointerIndex: Int,
        historyIndex: Int,
    ): Float {
        return event.getHistoricalY(pointerIndex, historyIndex) + event.rawY - event.y
    }
}
