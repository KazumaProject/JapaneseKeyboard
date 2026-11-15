package com.kazumaproject.markdownhelperkeyboard.keyboard_presentation

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.WindowManager
import androidx.core.view.ViewCompat
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType

internal fun keyboardPresentationWidth(context: Context, anchor: View, landscape: Boolean): Int {
    val metrics = anchor.resources.displayMetrics
    val currentLandscape = anchor.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val area = com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.FloatingWindowCoordinates(manager).safeArea(anchor)
    if (landscape == currentLandscape) return area.width().coerceAtLeast(1)
    val cutout = ViewCompat.getRootWindowInsets(anchor)?.displayCutout
    val horizontal = (cutout?.safeInsetLeft ?: 0) + (cutout?.safeInsetRight ?: 0)
    val vertical = (cutout?.safeInsetTop ?: 0) + (cutout?.safeInsetBottom ?: 0)
    val blocked = if (landscape) maxOf(horizontal, vertical) else minOf(horizontal, vertical)
    val physical = if (landscape) maxOf(metrics.widthPixels, metrics.heightPixels) else minOf(metrics.widthPixels, metrics.heightPixels)
    return (physical - blocked).coerceAtLeast(1)
}

internal fun requestedKeyboardForOrientation(requested: KeyboardType, landscape: Boolean,
    forceQwerty: Boolean, forceRomaji: Boolean): KeyboardType =
    if (!landscape || !forceQwerty) requested else if (forceRomaji) KeyboardType.ROMAJI else KeyboardType.QWERTY
