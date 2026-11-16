package com.kazumaproject.custom_keyboard.view

import android.graphics.Path
import android.graphics.RectF
import com.kazumaproject.custom_keyboard.data.PopupRegion

/** The tap and default outer popup share the same proportional corner radius. */
fun RectF.popupRoundedPath(): Path = Path().apply {
    val radius = minOf(width(), height()) * .16f
    addRoundRect(this@popupRoundedPath, radius, radius, Path.Direction.CW)
}

/** One path construction for the editor, around-key popup and projected guide. */
fun PopupRegion.outlinePath(originX: Float, originY: Float, keyWidth: Float, keyHeight: Float): Path {
    val rect = RectF(originX + bounds.left * keyWidth, originY + bounds.top * keyHeight,
        originX + bounds.right * keyWidth, originY + bounds.bottom * keyHeight)
    val shape = outline
    return Path().apply {
        when {
            shape == null -> {
                addPath(rect.popupRoundedPath())
            }
            shape.isCircle -> addCircle(rect.centerX(), rect.centerY(), minOf(rect.width(), rect.height()) / 2, Path.Direction.CW)
            else -> {
                shape.vertices.forEachIndexed { index, point ->
                    val x = rect.left + point.x * rect.width(); val y = rect.top + point.y * rect.height()
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        }
    }
}
