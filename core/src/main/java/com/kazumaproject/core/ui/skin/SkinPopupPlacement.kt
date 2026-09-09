package com.kazumaproject.core.ui.skin

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
                                   val windowElevation: Float, val animationStyle: Int,
                                   val clipping: Boolean, val touchable: Boolean, val laidOutInScreen: Boolean?,
                                   val contentLayout: ViewGroup.LayoutParams?)
    /** A direction change is one child layout transaction inside a stationary window. */
    private class FlickFrame(context: android.content.Context) : FrameLayout(context)

    private val legacyStates = java.util.WeakHashMap<KeyWindowLayout, LegacyState>()

    @JvmStatic fun restoreLegacy(bubble: KeyWindowLayout) {
        legacyStates.remove(bubble)?.let { saved ->
            bubble.elevation = saved.elevation
            bubble.setPadding(saved.padding[0],saved.padding[1],saved.padding[2],saved.padding[3])
            saved.window.get()?.let { window ->
                window.elevation=saved.windowElevation
                window.animationStyle=saved.animationStyle
                window.isClippingEnabled=saved.clipping
                window.isTouchable=saved.touchable
                SkinPopupWindowCompat.restoreScreenLayout(window, saved.laidOutInScreen)
                (window.contentView as? FlickFrame)?.let { frame ->
                    window.dismiss()
                    frame.removeView(bubble)
                    bubble.layoutParams = saved.contentLayout ?: FrameLayout.LayoutParams(-2, -2)
                    window.contentView = bubble
                }
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
            }, java.lang.ref.WeakReference(window), window.elevation, window.animationStyle,
                window.isClippingEnabled, window.isTouchable, SkinPopupWindowCompat.savedScreenLayout(window), bubble.layoutParams)
        }
        val layout = SkinPopupGeometry.resolve(w, h, direction, flick)
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
        if (flick) {
            // iOS changes directional surfaces in place. Resizing/repositioning a native
            // PopupWindow on every MOVE lets its compositor interpolate unrelated bounds.
            val union = android.graphics.Rect()
            PopupDirection.entries.forEach { union.union(SkinPopupGeometry.resolve(w, h, it, true).bounds) }
            if (window.contentView !is FlickFrame) {
                val frame = FlickFrame(anchor.context)
                window.dismiss()
                (bubble.parent as? ViewGroup)?.removeView(bubble)
                window.contentView = frame
                frame.addView(bubble)
            }
            val position = IntArray(2)
            anchor.getLocationOnScreen(position)
            val screenBounds = android.graphics.Rect(layout.bounds).apply { offset(position[0], position[1]) }
            val fitted = SkinPopupViewport.fit(screenBounds, SkinPopupViewport.bounds(anchor))
            bubble.layoutParams = FrameLayout.LayoutParams(layout.bounds.width(), layout.bounds.height()).apply {
                leftMargin = fitted.left - position[0] - union.left
                topMargin = fitted.top - position[1] - union.top
            }
            val resized = window.width != union.width() || window.height != union.height()
            window.width = union.width()
            window.height = union.height()
            window.isTouchable = false
            window.isClippingEnabled = false
            val windowPosition = SkinPopupWindowCompat.position(
                window, anchor, position[0] + union.left, position[1] + union.top)
            val x = windowPosition.x
            val y = windowPosition.y
            // Drop-down placement also fits the transparent frame to the visible screen;
            // that silently displaces the actual balloon on bottom/edge keys.
            if (!window.isShowing) window.showAtLocation(anchor, android.view.Gravity.NO_GRAVITY, x, y)
            else if (resized) window.update(x, y, window.width, window.height)
        } else {
            (window.contentView as? FlickFrame)?.let { frame ->
                window.dismiss()
                frame.removeView(bubble)
                bubble.layoutParams = legacyStates[bubble]?.contentLayout ?: FrameLayout.LayoutParams(-2, -2)
                window.contentView = bubble
            }
            legacyStates[bubble]?.let { saved ->
                SkinPopupWindowCompat.restoreScreenLayout(window, saved.laidOutInScreen)
                window.isClippingEnabled = saved.clipping
                window.isTouchable = saved.touchable
            }
            window.width = layout.bounds.width()
            window.height = layout.bounds.height()
            if (window.isShowing) window.update(anchor, layout.bounds.left, layout.bounds.top - h, window.width, window.height)
            else window.showAsDropDown(anchor, layout.bounds.left, layout.bounds.top - h)
        }
        return true
    }
}
