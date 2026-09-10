package com.kazumaproject.core.ui.skin

import android.graphics.Point
import android.graphics.Rect
import android.view.View
import androidx.core.view.WindowInsetsCompat

/** Screen space available to skin surfaces, without changing the IME's measured height. */
internal object SkinPopupViewport {
    fun bounds(anchor: View): Rect {
        val size = Point()
        @Suppress("DEPRECATION")
        anchor.display.getRealSize(size)
        val insets = anchor.rootWindowInsets?.let {
            WindowInsetsCompat.toWindowInsetsCompat(it, anchor).getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        }
        return Rect(insets?.left ?: 0, insets?.top ?: 0,
            size.x - (insets?.right ?: 0), size.y - (insets?.bottom ?: 0))
    }

    fun fit(surface: Rect, viewport: Rect): Rect = Rect(surface).apply {
        val dx = if (width() <= viewport.width()) {
            left.coerceIn(viewport.left, viewport.right - width()) - left
        } else viewport.left - left
        val dy = if (height() <= viewport.height()) {
            top.coerceIn(viewport.top, viewport.bottom - height()) - top
        } else viewport.top - top
        offset(dx, dy)
    }
}
