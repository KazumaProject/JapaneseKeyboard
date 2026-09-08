package com.kazumaproject.core.ui.skin

import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import com.kazumaproject.core.domain.skin.KeyboardSkinId

/** Measured on iOS 26.4.1 (23E254a), iPhone 17 Pro Max, at 3 pixels/point. */
internal class CupertinoSkin(override val id: KeyboardSkinId) : KeyboardSkin {
    override val palette = if (id == KeyboardSkinId.CUPERTINO_DARK) {
        SkinPalette(0xff171717.toInt(), 0xff3d3d3d.toInt(), 0xffffffff.toInt(),
            0xff262626.toInt(), 0xff0091ff.toInt(), 0xffffffff.toInt())
    } else {
        SkinPalette(0xffe2e4e8.toInt(), 0xffffffff.toInt(), 0xff000000.toInt(),
            0xffe7e8ec.toInt(), 0xff0088ff.toInt(), 0xffffffff.toInt())
    }

    override fun keyboardDrawable(resources: Resources, floating: Boolean): Drawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(palette.background)
            val radius = 28f * resources.displayMetrics.density
            val bottom = if (floating) radius else 0f
            cornerRadii = floatArrayOf(radius, radius, radius, radius, bottom, bottom, bottom, bottom)
        }

    override fun keyDrawable(resources: Resources, qwerty: Boolean): Drawable {
        val radius = (if (qwerty) 8f else 12.5f) * resources.displayMetrics.density
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), CupertinoSurfaceDrawable(palette.pressed, radius))
            addState(intArrayOf(android.R.attr.state_selected), CupertinoSurfaceDrawable(palette.selection, radius))
            addState(intArrayOf(), CupertinoSurfaceDrawable(palette.key, radius))
        }
    }

    override fun popupDrawable(resources: Resources, direction: PopupDirection, selected: Boolean): Drawable =
        CupertinoPopupDrawable(if (selected) palette.selection else palette.key,
            resources.displayMetrics.density, direction, id == KeyboardSkinId.CUPERTINO_DARK, selected)

    override fun configurePopupText(view: android.widget.TextView, flick: Boolean) {
        view.textSize = if (flick) 29.5f else 25f
        view.includeFontPadding = false
        view.gravity = android.view.Gravity.CENTER
        view.translationY = (if (flick) 1.3333f else .6667f) * view.resources.displayMetrics.density
    }

    override fun configurePreviewText(view: android.widget.TextView) {
        view.textSize = 37f
        view.includeFontPadding = false
        view.gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
        // Explicit baseline, independently measured from the popup cap and glyph outline.
        // The native fallback font has a deeper descender than the reference font.
        // Keep descending previews optically aligned without moving the popup or hit area.
        val hasDescender = view.text.any { it in "gjpqy" }
        val baseline = (if (hasDescender) 45f else 49f) * view.resources.displayMetrics.density
        view.setPadding(0, (baseline + view.paint.fontMetricsInt.ascent).toInt().coerceAtLeast(0), 0, 0)
    }

    override fun keyPreview(resources: Resources, keyWidth: Int, keyHeight: Int, keyLeft: Int,
                            screenWidth: Int, lowerRow: Boolean): SkinKeyPreview {
        val layout = CupertinoKeyPreviewGeometry.resolve(keyWidth, keyHeight, keyLeft, screenWidth)
        val lowerPosition = if (lowerRow) ((keyLeft + keyWidth / 2f) / screenWidth - 140f / 1320f) /
            ((1180f - 140f) / 1320f) else null
        return SkinKeyPreview(layout.width, layout.height, layout.xOffset, layout.yOffset,
            CupertinoKeyPreviewDrawable(id == KeyboardSkinId.CUPERTINO_DARK, keyWidth.toFloat(),
                -layout.xOffset.toFloat(), lowerPosition))
    }

    override fun guideDrawable(resources: Resources, direction: PopupDirection, selected: Boolean): Drawable {
        // Adjacent cells meet with square edges; only the outside of the cross is rounded.
        val corners = when(direction) {
            PopupDirection.LEFT -> 12
            PopupDirection.TOP -> 9
            PopupDirection.RIGHT -> 3
            PopupDirection.BOTTOM -> 6
            else -> 0
        }
        return CupertinoSurfaceDrawable(if(selected) palette.selection else palette.key,
            10.25f * resources.displayMetrics.density, corners,
            direction.takeIf { !selected && id == KeyboardSkinId.CUPERTINO_DARK && it != PopupDirection.CENTER })
    }

    override fun variationDrawable(resources: Resources): Drawable =
        CupertinoSurfaceDrawable(palette.key, 10f * resources.displayMetrics.density,
            variationIllumination = id == KeyboardSkinId.CUPERTINO_DARK)

    // Direct display timestamps target approximately 75 ms after event dispatch.
    // The timer excludes Android window/compositor presentation latency.
    // This is a visual hold only: gesture ownership and text commits finish immediately.
    override val popupReleaseDelayMillis: Long = 34L
    override val longPressLabelColor: Int =
        if (id == KeyboardSkinId.CUPERTINO_DARK) 0xff545454.toInt() else 0xff737373.toInt()
    override val longPressLabelFadeMillis: Long = 300L
    override val longPressLabelRestoreMillis: Long = 230L
    override val longPressLabelInterpolator = labelInterpolator(longPressLabelFadeMillis,
        if (id == KeyboardSkinId.CUPERTINO_LIGHT) 25.0 else 24.0)
    override val longPressLabelRestoreInterpolator = android.animation.TimeInterpolator { fraction ->
        // The captured release has a small fast onset followed by a slower return.
        // Keep this continuous and normalized; no initial seek or delayed input commit.
        fun response(milliseconds: Double): Double {
            val t = milliseconds * if (id == KeyboardSkinId.CUPERTINO_LIGHT) 0.026 else 0.024
            val onsetMillis = if (id == KeyboardSkinId.CUPERTINO_LIGHT) 2.0 else 12.0
            return 0.12 * (1 - kotlin.math.exp(-milliseconds / onsetMillis)) +
                0.88 * (1 - (1 + t) * kotlin.math.exp(-t))
        }
        (response(fraction * longPressLabelRestoreMillis.toDouble()) /
            response(longPressLabelRestoreMillis.toDouble())).toFloat()
    }

    private fun labelInterpolator(durationMillis: Long, ratePerSecond: Double) = android.animation.TimeInterpolator { fraction ->
        val end = durationMillis * ratePerSecond / 1000.0
        val t = fraction * end
        ((1 - (1 + t) * kotlin.math.exp(-t)) / (1 - (1 + end) * kotlin.math.exp(-end))).toFloat()
    }

    override fun showPopup(view: View) {
        view.animate().cancel()
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
    }

    override fun clearPopup(view: View) {
        view.animate().cancel()
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
    }
}
