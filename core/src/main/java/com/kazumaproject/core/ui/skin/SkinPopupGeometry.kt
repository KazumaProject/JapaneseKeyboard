package com.kazumaproject.core.ui.skin

import android.graphics.Rect
import kotlin.math.roundToInt

/** Measured against a 86 x 56 pt kana key. Offsets are relative to the anchor's top left. */
object SkinPopupGeometry {
    data class Layout(val bounds: Rect, val textInsets: Rect)
    fun resolve(width: Int, height: Int, direction: PopupDirection, flick: Boolean): Layout {
        if (!flick) {
            val x = when (direction) { PopupDirection.LEFT -> -width; PopupDirection.RIGHT -> width; else -> 0 }
            val y = when (direction) { PopupDirection.TOP -> -height; PopupDirection.BOTTOM -> height; else -> 0 }
            return Layout(Rect(x, y, x + width, y + height), Rect())
        }
        val horizontalTip = (width * (20f / 86f)).roundToInt()
        val verticalTip = (height * (70f / 168f)).roundToInt()
        val verticalShift = (height * (10f / 56f)).roundToInt()
        return when (direction) {
            PopupDirection.LEFT -> Layout(Rect(-width, 0, horizontalTip, height), Rect(0,0,horizontalTip,0))
            PopupDirection.RIGHT -> Layout(Rect(width-horizontalTip, 0, width*2, height), Rect(horizontalTip,0,0,0))
            PopupDirection.TOP -> Layout(Rect(0,-height-verticalShift,width,verticalTip-verticalShift), Rect(0,0,0,verticalTip))
            PopupDirection.BOTTOM -> Layout(Rect(0,height+verticalShift-verticalTip,width,height*2+verticalShift), Rect(0,verticalTip,0,0))
            else -> Layout(Rect(0,0,width,height), Rect())
        }
    }
}
