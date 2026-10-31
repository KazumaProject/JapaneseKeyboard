package com.kazumaproject.custom_keyboard.controller

import android.view.View

internal fun getLocationRelativeToWindowAnchor(
    keyAnchor: View,
    windowAnchor: View?
): IntArray {
    val resolvedWindowAnchor = windowAnchor ?: keyAnchor

    if (keyAnchor === resolvedWindowAnchor) {
        return IntArray(2).also { keyAnchor.getLocationInWindow(it) }
    }

    val keyLocation = IntArray(2)
    val windowLocation = IntArray(2)

    keyAnchor.getLocationOnScreen(keyLocation)
    resolvedWindowAnchor.getLocationOnScreen(windowLocation)

    return intArrayOf(
        keyLocation[0] - windowLocation[0],
        keyLocation[1] - windowLocation[1]
    )
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
        return
    }

    keyAnchor.getLocationOnScreen(keyLocationScratch)
    resolvedWindowAnchor.getLocationOnScreen(windowLocationScratch)

    outLocation[0] = keyLocationScratch[0] - windowLocationScratch[0]
    outLocation[1] = keyLocationScratch[1] - windowLocationScratch[1]
}
