package com.kazumaproject.core.ui.skin

import android.graphics.Point
import android.os.Build
import android.view.View
import android.widget.PopupWindow

/** PopupWindow gained public screen-layout accessors in Android 10. */
internal object SkinPopupWindowCompat {
    fun savedScreenLayout(window: PopupWindow): Boolean? =
        if (Build.VERSION.SDK_INT >= 29) window.isLaidOutInScreen else null

    fun restoreScreenLayout(window: PopupWindow, saved: Boolean?) {
        if (Build.VERSION.SDK_INT >= 29 && saved != null) window.setIsLaidOutInScreen(saved)
    }

    /** Convert a fitted screen position to the coordinate space used by showAtLocation/update. */
    fun position(window: PopupWindow, anchor: View, screenX: Int, screenY: Int): Point {
        if (Build.VERSION.SDK_INT >= 29) {
            window.setIsLaidOutInScreen(true)
            return Point(screenX, screenY)
        }
        val screen = IntArray(2)
        val inWindow = IntArray(2)
        anchor.getLocationOnScreen(screen)
        anchor.getLocationInWindow(inWindow)
        return Point(screenX - (screen[0] - inWindow[0]), screenY - (screen[1] - inWindow[1]))
    }
}
