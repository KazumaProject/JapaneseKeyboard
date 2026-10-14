package com.kazumaproject.core.ui.skin

import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import com.kazumaproject.core.domain.skin.KeyboardSkinId

/** Neon caps light from underneath native labels; the IME adds trails and celebrations. */
internal class FeverSkin : KeyboardSkin {
    override val id = KeyboardSkinId.FEVER
    override val palette = SkinPalette(
        background = 0xff0a0b1c.toInt(), key = 0xff18243c.toInt(), text = 0xffedfaff.toInt(),
        pressed = 0xff244b63.toInt(), selection = 0xffb6276a.toInt(), selectionText = Color.WHITE,
        specialKey = 0xff242039.toInt(), specialText = 0xffe6bffd.toInt(),
        spaceKey = 0xff17323f.toInt(), spaceText = 0xff9dfff0.toInt(),
    )
    override fun keyboardDrawable(resources: Resources, floating: Boolean): Drawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xff19132c.toInt(), palette.background, 0xff082b35.toInt())).apply {
            cornerRadius = 18f * resources.displayMetrics.density
            setStroke((resources.displayMetrics.density).toInt().coerceAtLeast(1), 0xff4d446e.toInt())
        }

    override fun keyDrawable(resources: Resources, qwerty: Boolean, role: SkinKeyRole): Drawable {
        val color = when (role) {
            SkinKeyRole.CHARACTER -> palette.key
            SkinKeyRole.MODIFIER -> palette.specialKey
            SkinKeyRole.SPACE -> palette.spaceKey
        }
        return FeverKeyDrawable(StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), surface(resources, palette.pressed, 0xff68ffee.toInt(), qwerty))
            addState(intArrayOf(android.R.attr.state_selected), surface(resources, palette.selection, 0xffffd578.toInt(), qwerty))
            addState(intArrayOf(), surface(resources, color,
                if (role == SkinKeyRole.MODIFIER) 0xff9869ad.toInt() else 0xff458697.toInt(), qwerty))
        }, resources.displayMetrics.density, (if (qwerty) 7f else 11f) * resources.displayMetrics.density)
    }
    private fun surface(resources: Resources, color: Int, edge: Int, qwerty: Boolean = false) =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(blend(color, Color.WHITE, .08f), color, blend(color, Color.BLACK, .25f))).apply {
            cornerRadius = (if (qwerty) 7f else 11f) * resources.displayMetrics.density
            setStroke(resources.displayMetrics.density.toInt().coerceAtLeast(1), edge)
        }
    override fun popupDrawable(resources: Resources, direction: PopupDirection, selected: Boolean): Drawable =
        surface(resources, if (selected) palette.selection else palette.key,
            if (selected) 0xffffd578.toInt() else 0xff67dfed.toInt())
    override fun showPopup(view: View) { clearPopup(view) }
    override fun clearPopup(view: View) {
        view.animate().cancel(); view.alpha = 1f; view.scaleX = 1f; view.scaleY = 1f
    }
    private fun blend(a: Int, b: Int, amount: Float) = Color.rgb(
        (Color.red(a) * (1 - amount) + Color.red(b) * amount).toInt(),
        (Color.green(a) * (1 - amount) + Color.green(b) * amount).toInt(),
        (Color.blue(a) * (1 - amount) + Color.blue(b) * amount).toInt())
}
