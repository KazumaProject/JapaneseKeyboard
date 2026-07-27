package com.kazumaproject.markdownhelperkeyboard.gemma.handwriting

import kotlin.math.abs

internal enum class CursorKeyGesture {
    Tap,
    FlickUp,
    FlickDown,
    Cancelled,
}

internal object CursorKeyFlickResolver {
    fun resolve(
        deltaX: Float,
        deltaY: Float,
        threshold: Float,
    ): CursorKeyGesture {
        val minimumDistance = threshold.coerceAtLeast(0f)
        val horizontalDistance = abs(deltaX)
        val verticalDistance = abs(deltaY)
        return when {
            verticalDistance >= minimumDistance && verticalDistance > horizontalDistance ->
                if (deltaY < 0f) CursorKeyGesture.FlickUp else CursorKeyGesture.FlickDown

            horizontalDistance < minimumDistance && verticalDistance < minimumDistance ->
                CursorKeyGesture.Tap

            else -> CursorKeyGesture.Cancelled
        }
    }
}
