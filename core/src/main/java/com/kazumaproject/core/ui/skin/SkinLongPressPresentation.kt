package com.kazumaproject.core.ui.skin

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.kazumaproject.core.domain.skin.KeyboardSkinId

/** iOS dims labels during the kana guide, while leaving key surfaces unchanged. */
class SkinLongPressPresentation {
    private val colors = mutableMapOf<TextView, ColorStateList>()
    fun show(root: View, id: KeyboardSkinId) {
        clear()
        if (KeyboardSkinRegistry.find(id) == null) return
        val color = if (id == KeyboardSkinId.CUPERTINO_DARK) 0xff545454.toInt() else 0xff737373.toInt()
        fun visit(view: View) {
            if (view is TextView) { colors[view] = view.textColors; view.setTextColor(color) }
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        visit(root)
    }
    fun clear() {
        colors.forEach { (view, color) -> view.setTextColor(color) }
        colors.clear()
    }
}
