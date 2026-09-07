package com.kazumaproject.core.ui.skin

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.PopupWindow
import android.widget.TextView
import com.kazumaproject.core.ui.key_window.KeyWindowLayout

/** Keeps anchors and input coordinates intact; only the popup surface changes. */
object SkinPopupPlacement {
    fun show(window: PopupWindow, bubble: KeyWindowLayout, anchor: View,
             direction: PopupDirection, flick: Boolean): Boolean {
        val skin = KeyboardSkinRegistry.find(bubble.skinId) ?: return false
        val w = anchor.width
        val h = anchor.height
        if (w <= 0 || h <= 0) return true
        window.dismiss()
        val horizontal = direction == PopupDirection.LEFT || direction == PopupDirection.RIGHT
        window.width = if (flick && horizontal) w * 3 / 2 else w
        window.height = if (flick && !horizontal && direction != PopupDirection.CENTER) h * 3 / 2 else h
        bubble.skinDirection = if (flick) direction else PopupDirection.CENTER
        bubble.skinSelected = !flick && direction == PopupDirection.CENTER
        val inset = if (flick) (if (horizontal) w else h) / 2 else 0
        bubble.setPadding(if (direction == PopupDirection.RIGHT) inset else 0,
            if (direction == PopupDirection.BOTTOM) inset else 0,
            if (direction == PopupDirection.LEFT) inset else 0,
            if (direction == PopupDirection.TOP) inset else 0)
        for (i in 0 until bubble.childCount) (bubble.getChildAt(i) as? TextView)?.let {
            it.setTextColor(if (bubble.skinSelected) skin.palette.selectionText else skin.palette.text)
            it.gravity = android.view.Gravity.CENTER
            it.includeFontPadding = false
        }
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.elevation = 0f
        window.animationStyle = 0
        skin.showPopup(bubble)
        val x = when (direction) { PopupDirection.LEFT -> -w; PopupDirection.RIGHT -> if (flick) w / 2 else w; else -> 0 }
        val y = when (direction) { PopupDirection.TOP -> -2 * h; PopupDirection.BOTTOM -> if (flick) -h / 2 else 0; else -> -h }
        window.showAsDropDown(anchor, x, y)
        return true
    }
}
