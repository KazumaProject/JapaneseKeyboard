package com.kazumaproject.custom_keyboard.controller

import com.kazumaproject.custom_keyboard.data.TfbiGuideFingerPosition
import com.kazumaproject.custom_keyboard.data.TfbiGuideGrid
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection

/**
 * Resolves the same 3x3 cells that the guide view renders.
 *
 * The guide is intentionally rectangular: the finger marker is placed from the key-local
 * coordinate, so using the same cells for classification keeps the highlighted output and the
 * committed output in agreement even when a key is wider than it is tall.
 */
internal fun resolveTfbiGuideGridDirection(
    position: TfbiGuideFingerPosition,
    enabledDirections: Set<TfbiFlickDirection>
): TfbiFlickDirection {
    if (enabledDirections.isEmpty()) return TfbiFlickDirection.TAP

    val x = position.x.coerceIn(0f, 1f)
    val y = position.y.coerceIn(0f, 1f)
    val cellDirection = TfbiGuideGrid.directionAt(column(x), row(y))
    if (cellDirection == TfbiFlickDirection.TAP) return TfbiFlickDirection.TAP
    if (cellDirection in enabledDirections) return cellDirection

    // Some maps omit a diagonal or another cell. Keep the gesture usable by selecting the
    // enabled cell whose center is closest to the marker instead of returning an invalid node.
    return enabledDirections
        .minByOrNull { direction ->
            val cell = TfbiGuideGrid.cellOf(direction)
            val centerX = (cell.first + 0.5f) / 3f
            val centerY = (cell.second + 0.5f) / 3f
            val dx = x - centerX
            val dy = y - centerY
            dx * dx + dy * dy
        }
        ?: TfbiFlickDirection.TAP
}

internal fun isTfbiGuideTapCell(position: TfbiGuideFingerPosition): Boolean {
    val x = position.x.coerceIn(0f, 1f)
    val y = position.y.coerceIn(0f, 1f)
    return TfbiGuideGrid.directionAt(column(x), row(y)) == TfbiFlickDirection.TAP
}

private fun column(value: Float): Int = when {
    value < 1f / 3f -> 0
    value < 2f / 3f -> 1
    else -> 2
}

private fun row(value: Float): Int = when {
    value < 1f / 3f -> 0
    value < 2f / 3f -> 1
    else -> 2
}
