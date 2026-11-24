package com.kazumaproject.core.ui.skin

import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import kotlin.math.roundToInt

internal class EInkSkin : KeyboardSkin {
    override val id = KeyboardSkinId.E_INK
    override val palette = SkinPalette(
        background = Color.WHITE,
        key = Color.WHITE,
        text = Color.BLACK,
        pressed = 0xffbfbfbf.toInt(),
        selection = Color.BLACK,
        selectionText = Color.WHITE,
        specialKey = 0xffededed.toInt(),
    )

    override fun keyboardDrawable(resources: Resources, floating: Boolean) =
        GradientDrawable().apply {
            setColor(palette.background)
            if (floating) cornerRadius = 6f * resources.displayMetrics.density
        }

    override fun keyDrawable(resources: Resources, qwerty: Boolean, role: SkinKeyRole): StateListDrawable {
        val idleColor = when (role) {
            SkinKeyRole.CHARACTER -> palette.key
            SkinKeyRole.MODIFIER -> palette.specialKey
            SkinKeyRole.SPACE -> palette.spaceKey
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), outlinedSurface(resources, palette.pressed))
            addState(intArrayOf(android.R.attr.state_selected), outlinedSurface(resources, palette.pressed))
            addState(intArrayOf(), outlinedSurface(resources, idleColor))
        }
    }

    override fun popupDrawable(resources: Resources, direction: PopupDirection, selected: Boolean) =
        outlinedSurface(resources, if (selected) palette.selection else palette.key)

    override fun keyPreview(
        resources: Resources,
        keyWidth: Int,
        keyHeight: Int,
        keyLeft: Int,
        screenWidth: Int,
        lowerRow: Boolean,
    ): SkinKeyPreview {
        val density = resources.displayMetrics.density
        val width = maxOf(keyWidth, (44f * density).roundToInt()).coerceAtMost(screenWidth)
        val height = maxOf(keyHeight, (44f * density).roundToInt())
        val left = (keyLeft + (keyWidth - width) / 2).coerceIn(0, screenWidth - width)
        return SkinKeyPreview(
            width, height, left - keyLeft, -keyHeight - height - (2f * density).roundToInt(),
            popupDrawable(resources, PopupDirection.PREVIEW),
        )
    }

    override fun configurePreviewText(view: TextView) {
        view.gravity = Gravity.CENTER
    }

    override fun showPopup(view: View) {
        view.animate().cancel()
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
    }

    override fun clearPopup(view: View) = showPopup(view)

    private fun outlinedSurface(resources: Resources, fill: Int) = GradientDrawable().apply {
        val density = resources.displayMetrics.density
        setColor(fill)
        cornerRadius = 4f * density
        setStroke(density.roundToInt().coerceAtLeast(1), Color.BLACK)
    }
}
