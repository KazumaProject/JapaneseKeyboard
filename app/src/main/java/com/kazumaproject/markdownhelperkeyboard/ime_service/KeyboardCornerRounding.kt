package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable

/** Applies the user's corner shape while keeping the skin's existing background drawable. */
internal fun applyClassicKeyboardCornerRounding(
    drawable: Drawable,
    rounded: Boolean,
    radiusDp: Int,
    density: Float,
    topLeft: Boolean,
    topRight: Boolean,
    bottomRight: Boolean,
    bottomLeft: Boolean,
): Drawable {
    val gradient = drawable as? GradientDrawable ?: return drawable
    val radiusPx = if (rounded) radiusDp.coerceIn(0, 64) * density else 0f
    gradient.cornerRadii = floatArrayOf(
        if (rounded && topLeft) radiusPx else 0f,
        if (rounded && topLeft) radiusPx else 0f,
        if (rounded && topRight) radiusPx else 0f,
        if (rounded && topRight) radiusPx else 0f,
        if (rounded && bottomRight) radiusPx else 0f,
        if (rounded && bottomRight) radiusPx else 0f,
        if (rounded && bottomLeft) radiusPx else 0f,
        if (rounded && bottomLeft) radiusPx else 0f,
    )
    return gradient
}
