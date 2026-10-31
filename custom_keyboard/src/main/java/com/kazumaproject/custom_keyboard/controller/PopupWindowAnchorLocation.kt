package com.kazumaproject.custom_keyboard.controller

import android.view.View
import android.graphics.Matrix
import kotlin.math.roundToInt

/** Return an origin whose unscaled center coincides with the transformed key center. */
internal fun adjustPopupAnchorForTransform(keyAnchor: View, origin: IntArray) {
    if (keyAnchor.rotation == 0f) return
    val points = floatArrayOf(0f, 0f, keyAnchor.width / 2f, keyAnchor.height / 2f)
    val matrix = Matrix()
    keyAnchor.transformMatrixToGlobal(matrix)
    matrix.mapPoints(points)
    origin[0] += (points[2] - points[0] - keyAnchor.width / 2f).roundToInt()
    origin[1] += (points[3] - points[1] - keyAnchor.height / 2f).roundToInt()
}

internal fun getLocationRelativeToWindowAnchor(
    keyAnchor: View,
    windowAnchor: View?
): IntArray {
    val resolvedWindowAnchor = windowAnchor ?: keyAnchor

    if (keyAnchor === resolvedWindowAnchor) {
        return IntArray(2).also { keyAnchor.getLocationInWindow(it); adjustPopupAnchorForTransform(keyAnchor, it) }
    }

    val keyLocation = IntArray(2)
    val windowLocation = IntArray(2)

    keyAnchor.getLocationOnScreen(keyLocation)
    resolvedWindowAnchor.getLocationOnScreen(windowLocation)

    return intArrayOf(
        keyLocation[0] - windowLocation[0],
        keyLocation[1] - windowLocation[1]
    ).also { adjustPopupAnchorForTransform(keyAnchor, it) }
}

internal fun getLocationRelativeToWindowAnchor(
    keyAnchor: View,
    windowAnchor: View?,
    outLocation: IntArray,
    keyLocationScratch: IntArray,
    windowLocationScratch: IntArray
) {
    require(outLocation.size >= 2)
    require(keyLocationScratch.size >= 2)
    require(windowLocationScratch.size >= 2)

    val resolvedWindowAnchor = windowAnchor ?: keyAnchor

    if (keyAnchor === resolvedWindowAnchor) {
        keyAnchor.getLocationInWindow(outLocation)
        adjustPopupAnchorForTransform(keyAnchor, outLocation)
        return
    }

    keyAnchor.getLocationOnScreen(keyLocationScratch)
    resolvedWindowAnchor.getLocationOnScreen(windowLocationScratch)

    outLocation[0] = keyLocationScratch[0] - windowLocationScratch[0]
    outLocation[1] = keyLocationScratch[1] - windowLocationScratch[1]
    adjustPopupAnchorForTransform(keyAnchor, outLocation)
}
