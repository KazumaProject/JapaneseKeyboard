package com.kazumaproject.core.ui.skin

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.PopupWindow
import android.widget.TextView
import com.kazumaproject.core.ui.key_window.KeyWindowLayout

/** Keeps anchors and input coordinates intact; only the popup surface changes. */
object SkinPopupPlacement {
    private data class LabelState(val index: Int, val gravity: Int, val fontPadding: Boolean,
                                  val translationY: Float, val textSize: Float)
    private data class LegacyState(val elevation: Float, val padding: IntArray,
                                   val labels: List<LabelState>,
                                   val window: java.lang.ref.WeakReference<PopupWindow>,
                                   val windowElevation: Float, val animationStyle: Int)
    private val legacyStates = java.util.WeakHashMap<KeyWindowLayout, LegacyState>()

    @JvmStatic fun restoreLegacy(bubble: KeyWindowLayout) {
        legacyStates.remove(bubble)?.let { saved ->
            bubble.elevation = saved.elevation
            bubble.setPadding(saved.padding[0],saved.padding[1],saved.padding[2],saved.padding[3])
            saved.window.get()?.let { window ->
                window.elevation=saved.windowElevation
                window.animationStyle=saved.animationStyle
            }
            saved.labels.forEach { label ->
                (bubble.getChildAt(label.index) as? TextView)?.let { text ->
                    text.gravity = label.gravity
                    text.includeFontPadding = label.fontPadding
                    text.translationY = label.translationY
                    text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,label.textSize)
                }
            }
        }
    }

    fun show(window: PopupWindow, bubble: KeyWindowLayout, anchor: View,
             direction: PopupDirection, flick: Boolean): Boolean {
        val skin = KeyboardSkinRegistry.find(bubble.skinId)
        if (skin == null) { restoreLegacy(bubble); return false }
        val w = anchor.width
        val h = anchor.height
        if (w <= 0 || h <= 0) return true
        if (!legacyStates.containsKey(bubble)) {
            legacyStates[bubble] = LegacyState(bubble.elevation, intArrayOf(bubble.paddingLeft,bubble.paddingTop,bubble.paddingRight,bubble.paddingBottom),
                (0 until bubble.childCount).mapNotNull {
                (bubble.getChildAt(it) as? TextView)?.let { text ->
                    LabelState(it, text.gravity, text.includeFontPadding, text.translationY, text.textSize)
                }
            }, java.lang.ref.WeakReference(window), window.elevation, window.animationStyle)
        }
        val layout = SkinPopupGeometry.resolve(w, h, direction, flick)
        window.width = layout.bounds.width()
        window.height = layout.bounds.height()
        bubble.skinDirection = direction
        bubble.skinGuide = !flick
        bubble.skinSelected = !flick && direction == PopupDirection.CENTER
        val inset = layout.textInsets
        bubble.setPadding(inset.left, inset.top, inset.right, inset.bottom)
        bubble.elevation = 0f
        for (i in 0 until bubble.childCount) (bubble.getChildAt(i) as? TextView)?.let {
            it.setTextColor(if (bubble.skinSelected) skin.palette.selectionText else skin.palette.text)
            it.gravity = android.view.Gravity.CENTER
            it.includeFontPadding = false
            skin.configurePopupText(it, flick)
        }
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.elevation = 0f
        window.animationStyle = 0
        skin.showPopup(bubble)
        val x = layout.bounds.left
        val y = layout.bounds.top - h
        if (window.isShowing) window.update(anchor, x, y, window.width, window.height)
        else window.showAsDropDown(anchor, x, y)
        return true
    }
}
