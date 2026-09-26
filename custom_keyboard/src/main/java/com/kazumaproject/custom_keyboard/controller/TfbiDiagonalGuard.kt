package com.kazumaproject.custom_keyboard.controller

import com.kazumaproject.core.domain.flick.TfbiDiagonalRecognitionMode
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection

/** Keeps a second-stage diagonal from winning until the finger has clearly turned. */
internal fun guardTfbiSecondStageDiagonal(
    candidate: TfbiFlickDirection,
    entry: TfbiFlickDirection,
    deltaX: Float,
    deltaY: Float,
    thresholdPx: Float,
    enabledDirections: Set<TfbiFlickDirection>,
    mode: TfbiDiagonalRecognitionMode,
): TfbiFlickDirection {
    if (mode != TfbiDiagonalRecognitionMode.STABLE || entry !in enabledDirections) {
        return candidate
    }

    val turnDistance = when (entry to candidate) {
        TfbiFlickDirection.RIGHT to TfbiFlickDirection.UP_RIGHT,
        TfbiFlickDirection.LEFT to TfbiFlickDirection.UP_LEFT -> -deltaY
        TfbiFlickDirection.RIGHT to TfbiFlickDirection.DOWN_RIGHT,
        TfbiFlickDirection.LEFT to TfbiFlickDirection.DOWN_LEFT -> deltaY
        TfbiFlickDirection.UP to TfbiFlickDirection.UP_RIGHT,
        TfbiFlickDirection.DOWN to TfbiFlickDirection.DOWN_RIGHT -> deltaX
        TfbiFlickDirection.UP to TfbiFlickDirection.UP_LEFT,
        TfbiFlickDirection.DOWN to TfbiFlickDirection.DOWN_LEFT -> -deltaX
        else -> return candidate
    }

    return if (turnDistance >= thresholdPx) candidate else entry
}
