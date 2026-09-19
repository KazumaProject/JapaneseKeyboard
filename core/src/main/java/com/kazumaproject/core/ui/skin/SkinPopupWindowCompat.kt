package com.kazumaproject.core.ui.skin

import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.widget.PopupWindow
import java.lang.reflect.InvocationTargetException

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

    /**
     * Android 7/8 resolve PopupWindow.showAsDropDown() against the panel token itself.
     * That token is not accepted for a popup attached to an IME application window.
     * Use PopupWindow's hidden token overload on those releases so the popup is still
     * attached to the same top-level window without changing the normal popup path.
     */
    fun showInApplicationWindow(window: PopupWindow, anchor: View, screenX: Int, screenY: Int) {
        if (Build.VERSION.SDK_INT >= 29) {
            error("Application-window popup fallback is only for pre-29 Android")
        }
        val token = anchor.applicationWindowToken
            ?: error("Cannot show popup without an application window token")
        val point = position(window, anchor, screenX, screenY)
        try {
            showAtLocationWithToken.invoke(window, token, Gravity.NO_GRAVITY, point.x, point.y)
        } catch (error: InvocationTargetException) {
            throw (error.targetException as? RuntimeException) ?: error
        }
    }

    fun updateInApplicationWindow(window: PopupWindow, anchor: View,
                                  screenX: Int, screenY: Int, width: Int, height: Int) {
        val point = position(window, anchor, screenX, screenY)
        window.update(point.x, point.y, width, height)
    }

    private val showAtLocationWithToken by lazy {
        PopupWindow::class.java.getDeclaredMethod(
            "showAtLocation", IBinder::class.java, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply {
            isAccessible = true
        }
    }
}
