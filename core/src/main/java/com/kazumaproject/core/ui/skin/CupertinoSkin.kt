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
            resources.displayMetrics.density, direction)

    // No Material ripple, elevation animation, or delayed dismissal. The captured kana
    // states change with selection; presentation must never delay input or retain a popup.
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
