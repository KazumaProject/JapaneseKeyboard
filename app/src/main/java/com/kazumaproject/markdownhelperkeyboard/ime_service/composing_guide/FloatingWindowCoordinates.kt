package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager

/** Screen coordinates are authoritative; WindowManager's inset origin is translated only here. */
internal class FloatingWindowCoordinates(private val manager: WindowManager) {
    private val origin = Point()

    fun safeArea(anchor: View): Rect {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = manager.currentWindowMetrics
            origin.set(metrics.bounds.left, metrics.bounds.top)
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return Rect(metrics.bounds).apply {
                left += insets.left; top += insets.top; right -= insets.right; bottom -= insets.bottom
            }
        }
        val size = Point().also { manager.defaultDisplay.getRealSize(it) }
        val insets = anchor.rootWindowInsets
        val cutout = if (Build.VERSION.SDK_INT >= 28) insets?.displayCutout else null
        val safe = Rect(maxOf(insets?.stableInsetLeft ?: 0, cutout?.safeInsetLeft ?: 0),
            maxOf(insets?.stableInsetTop ?: 0, cutout?.safeInsetTop ?: 0),
            size.x - maxOf(insets?.stableInsetRight ?: 0, cutout?.safeInsetRight ?: 0),
            size.y - maxOf(insets?.stableInsetBottom ?: 0, cutout?.safeInsetBottom ?: 0))
        // Old IME decor can report zero stable insets while its visible display frame
        // correctly excludes a side navigation bar. Keep panels out of that system UI.
        val visible = Rect().also(anchor::getWindowVisibleDisplayFrame)
        if (!visible.isEmpty) safe.intersect(visible)
        return safe
    }

    fun configure(params: WindowManager.LayoutParams) {
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (Build.VERSION.SDK_INT >= 30) {
            params.setFitInsetsTypes(0)
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    fun position(params: WindowManager.LayoutParams, screenX: Int, screenY: Int) {
        params.x = screenX - origin.x
        params.y = screenY - origin.y
    }

    fun observeLegacyOrigin(view: View, params: () -> WindowManager.LayoutParams?, onChanged: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 30) return
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val requested = params() ?: return@addOnLayoutChangeListener
            val location = IntArray(2).also(view::getLocationOnScreen)
            val x = location[0] - requested.x
            val y = location[1] - requested.y
            if (origin.x != x || origin.y != y) {
                origin.set(x, y)
                view.post { if (view.isAttachedToWindow) onChanged() }
            }
        }
    }
}
