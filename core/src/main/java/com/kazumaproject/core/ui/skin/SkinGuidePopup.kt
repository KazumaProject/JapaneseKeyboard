package com.kazumaproject.core.ui.skin

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.PopupWindow
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.kazumaproject.core.ui.key_window.KeyWindowLayout

/**
 * Draw the guide in the anchor window's overlay when it fits. Guide and keyboard labels
 * then share a render transaction, without creating or retiring another surface.
 * The overlay never participates in input hit testing or keyboard layout measurement.
 */
class SkinGuidePopup(context: Context) {
    private val content = FrameLayout(context)
    private val cells = listOf(PopupDirection.CENTER, PopupDirection.LEFT, PopupDirection.TOP,
        PopupDirection.RIGHT, PopupDirection.BOTTOM).associateWith { direction ->
        KeyWindowLayout(context).apply {
            skinGuide = true
            skinDirection = direction
            setPadding(0, 0, 0, 0)
            addView(TextView(context).apply { gravity = Gravity.CENTER; includeFontPadding = false },
                FrameLayout.LayoutParams(-1, -1))
            content.addView(this)
        }
    }
    private var owner: ViewGroup? = null
    private var overflowWindow: PopupWindow? = null
    private var skin: KeyboardSkin? = null
    val isShowing: Boolean get() = (owner != null && content.isAttachedToWindow) || overflowWindow?.isShowing == true

    fun show(anchor: View, skin: KeyboardSkin, labels: Map<PopupDirection, CharSequence>): View {
        val root = anchor.rootView as? ViewGroup ?: error("Keyboard must have a window root")
        dismiss()
        val width = anchor.width
        val height = anchor.height
        configure(width, height, skin, labels)
        skin.showPopup(content)
        val anchorPosition = IntArray(2)
        val rootPosition = IntArray(2)
        anchor.getLocationOnScreen(anchorPosition)
        root.getLocationOnScreen(rootPosition)
        val left = anchorPosition[0] - rootPosition[0] - width
        val top = anchorPosition[1] - rootPosition[1] - height
        content.measure(
            View.MeasureSpec.makeMeasureSpec(3 * width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(3 * height, View.MeasureSpec.EXACTLY),
        )
        content.layout(left, top, left + 3 * width, top + 3 * height)
        val fitsRoot = cells.values.filter { it.visibility == View.VISIBLE }.all { cell ->
            left + cell.left >= 0 && top + cell.top >= 0 &&
                left + cell.right <= root.width && top + cell.bottom <= root.height
        }
        if (fitsRoot) {
            root.overlay.add(content)
            owner = root
        } else {
            // Floating/embedded keyboards can have a smaller window than their guide.
            // Preserve those overflow visuals instead of clipping them to that window.
            val popup = overflowWindow ?: PopupWindow(content, 0, 0, false).apply {
                isTouchable = false
                isClippingEnabled = false
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                elevation = 0f
                animationStyle = 0
                inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            }.also { overflowWindow = it }
            popup.width = 3 * width
            popup.height = 3 * height
            popup.showAsDropDown(anchor, -width, -2 * height)
        }
        return content
    }

    internal fun configure(width: Int, height: Int, skin: KeyboardSkin,
                           labels: Map<PopupDirection, CharSequence>): View {
        check(width > 0 && height > 0)
        this.skin = skin
        cells.forEach { (direction, cell) ->
            cell.skinId = skin.id
            val label = cell.getChildAt(0) as TextView
            label.text = labels[direction] ?: ""
            skin.configurePopupText(label, false)
            cell.visibility = if (label.text.isEmpty()) View.INVISIBLE else View.VISIBLE
            val bounds = SkinPopupGeometry.resolve(width, height, direction, false).bounds
            cell.layoutParams = FrameLayout.LayoutParams(width, height).apply {
                leftMargin = bounds.left + width
                topMargin = bounds.top + height
            }
        }
        select(PopupDirection.CENTER)
        return content
    }

    fun select(direction: PopupDirection) {
        val palette = skin?.palette ?: return
        // Empty alternatives never gain a visible selection; the keyboard owns commit semantics.
        cells.forEach { (position, cell) ->
            cell.skinSelected = position == direction
            (cell.getChildAt(0) as TextView).setTextColor(
                if (cell.skinSelected) palette.selectionText else palette.text)
        }
    }

    fun dismiss() {
        owner?.overlay?.remove(content)
        owner = null
        overflowWindow?.dismiss()
        skin?.clearPopup(content)
        skin = null
    }
}
