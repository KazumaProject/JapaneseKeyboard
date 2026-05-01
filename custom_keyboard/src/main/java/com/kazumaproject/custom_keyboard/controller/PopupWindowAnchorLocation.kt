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
