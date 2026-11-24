package com.kazumaproject.core.ui.skin

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import kotlin.math.roundToInt

/** A non-interactive preview of the current tap or flick output, centered above its key. */
class AboveKeyFlickPopup(private val font: () -> KeyboardFontSnapshot) {
    private val popups = mutableMapOf<View, PopupWindow>()

    var isEnabled = false
        set(value) {
            field = value
            if (!value) dismissAll()
        }

    fun show(anchor: View, text: String?, style: PopupViewStyle) {
        if (!isEnabled || text.isNullOrEmpty() || !anchor.isShown || !anchor.isAttachedToWindow) {
            dismiss(anchor)
            return
        }
        val visiblePopup = popups[anchor]
        if (visiblePopup?.isShowing == true && (visiblePopup.contentView as TextView).text == text) return
        val viewport = SkinPopupViewport.bounds(anchor)
        if (viewport.isEmpty) return

        val density = anchor.resources.displayMetrics.density
        val popup = popups.getOrPut(anchor) {
            val palette = KeyboardSkinRegistry.find(style.skinId)?.palette
            val backgroundColor = style.backgroundColor ?: palette?.key
                ?: MaterialColors.getColor(anchor, MaterialR.attr.colorSurface, 0xfff4f2f6.toInt())
            val horizontalPadding = (12 * density).roundToInt()
            PopupWindow(TextView(anchor.context).apply {
                gravity = Gravity.CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                isSingleLine = true
                setPadding(horizontalPadding, 0, horizontalPadding, 0)
                setTextColor(style.textColor ?: palette?.text
                    ?: MaterialColors.getColor(anchor, MaterialR.attr.colorOnSurface, 0xff242228.toInt()))
                background = GradientDrawable().apply {
                    cornerRadius = 10 * density
                    setColor(backgroundColor)
                }
                textSize = style.textSizeSp
                KeyboardFontApplicator.apply(this, font())
            }).apply {
                isTouchable = false
                isClippingEnabled = false
                inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
                animationStyle = 0
                setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
                elevation = 4 * density
            }
        }
        val label = popup.contentView as TextView
        label.text = text
        label.measure(
            View.MeasureSpec.makeMeasureSpec(viewport.width(), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val scale = style.sizeScalePercent / 100f
        val width = maxOf(label.measuredWidth, (anchor.width * 0.72f * scale).roundToInt())
            .coerceAtMost(viewport.width())
        val height = maxOf(label.measuredHeight + (8 * density).roundToInt(),
            (anchor.height * 0.8f * scale).roundToInt())
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val left = location[0] + (anchor.width - width) / 2
        val bottom = location[1] - (4 * density).roundToInt()
        val fitted = SkinPopupViewport.fit(Rect(left, bottom - height, left + width, bottom), viewport)

        try {
            if (SkinPopupPlacement.usesAnchoredWindow(anchor)) {
                val applicationView = SkinPopupPlacement.applicationWindowView(anchor)
                if (popup.isShowing) {
                    SkinPopupWindowCompat.updateInApplicationWindow(popup, applicationView,
                        fitted.left, fitted.top, width, height)
                } else {
                    popup.width = width
                    popup.height = height
                    SkinPopupWindowCompat.showInApplicationWindow(popup, anchor, applicationView,
                        fitted.left, fitted.top)
                }
            } else {
                val position = SkinPopupWindowCompat.position(popup, anchor, fitted.left, fitted.top)
                if (popup.isShowing) {
                    popup.update(position.x, position.y, width, height)
                } else {
                    popup.width = width
                    popup.height = height
                    popup.showAtLocation(anchor, Gravity.NO_GRAVITY, position.x, position.y)
                }
            }
        } catch (_: WindowManager.BadTokenException) {
            dismiss(anchor)
        }
    }

    fun dismiss(anchor: View?) {
        popups.remove(anchor)?.dismiss()
    }

    fun dismissAll() {
        popups.values.forEach(PopupWindow::dismiss)
        popups.clear()
    }
}
