package com.kazumaproject.core.ui.skin

import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.view.Gravity
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

    /**
     * Use the application token while placing the popup at an absolute screen position.
     * A split IME key has a panel token, but the popup must be attached to the parent
     * application window. showAsDropDown() is deliberately avoided here because the
     * framework may move the complete transparent popup surface to fit the display.
     */
    fun showInApplicationWindow(
        window: PopupWindow,
        anchor: View,
        applicationWindowView: View,
        screenX: Int,
        screenY: Int,
    ) {
        val token = anchor.applicationWindowToken
            ?: error("Cannot show popup without an application window token")
        if (Build.VERSION.SDK_INT >= 29) window.setIsLaidOutInScreen(true)
        // API 29+ accepts screen coordinates once FLAG_LAYOUT_IN_SCREEN is enabled;
        // older releases interpret the same values relative to the parent frame.
        val position = applicationWindowPosition(applicationWindowView, screenX, screenY)
        window.showAtLocation(ApplicationWindowTokenView(anchor, token),
            Gravity.NO_GRAVITY, position.x, position.y)
    }

    fun updateInApplicationWindow(
        window: PopupWindow,
        applicationWindowView: View,
        screenX: Int,
        screenY: Int,
        width: Int,
        height: Int,
    ) {
        val position = applicationWindowPosition(applicationWindowView, screenX, screenY)
        window.update(position.x, position.y, width, height)
    }

    private fun applicationWindowPosition(applicationWindowView: View, screenX: Int, screenY: Int): Point {
        if (Build.VERSION.SDK_INT >= 29) return Point(screenX, screenY)
        val screen = IntArray(2)
        val inWindow = IntArray(2)
        applicationWindowView.getLocationOnScreen(screen)
        applicationWindowView.getLocationInWindow(inWindow)
        return Point(screenX - (screen[0] - inWindow[0]), screenY - (screen[1] - inWindow[1]))
    }

    /**
     * PopupWindow's public showAtLocation(View, ...) obtains its token from the
     * supplied view. On pre-29 Android a split pane has a panel token, while the
     * popup must use the parent IME/application token. Keep the real root view so
     * framework versions that retain it for popup bookkeeping still see the pane's
     * root, while exposing the application token through the documented View API.
     */
    private class ApplicationWindowTokenView(anchor: View, private val token: IBinder) : View(anchor.context) {
        private val root = anchor.rootView

        override fun getWindowToken(): IBinder = token
        override fun getApplicationWindowToken(): IBinder = token
        override fun getRootView(): View = root
    }
}
