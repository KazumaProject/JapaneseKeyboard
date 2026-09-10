package com.kazumaproject.core.ui.skin

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import com.kazumaproject.core.domain.skin.KeyboardSkinId

/** Retains XML colors before even a cold-start skin. Owned by the keyboard view. */
class KeyboardSkinColorRestorer(root: View) {
    private val textColors = mutableMapOf<TextView, ColorStateList>()
    private val imageFilters = mutableMapOf<ImageView, android.graphics.ColorFilter?>()
    private val imageTints = mutableMapOf<ImageView, ColorStateList?>()

    init {
        fun capture(view: View) {
            if (view is TextView) textColors[view] = view.textColors
            if (view is ImageView) {
                imageTints[view] = ImageViewCompat.getImageTintList(view)
                imageFilters[view] = view.colorFilter
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) capture(view.getChildAt(index))
        }
        capture(root)
    }

    fun beforeSkinChange(previous: KeyboardSkinId, next: KeyboardSkinId) {
        if (previous == KeyboardSkinId.DEFAULT || next != KeyboardSkinId.DEFAULT) return
        textColors.forEach { (view, colors) -> view.setTextColor(colors) }
        imageFilters.forEach { (view, filter) -> view.colorFilter = filter }
        imageTints.forEach { (view, tint) ->
            if (ImageViewCompat.getImageTintList(view) != tint) ImageViewCompat.setImageTintList(view, tint)
        }
    }
}
