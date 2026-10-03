package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.graphics.Rect
import android.graphics.Point
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.PopupWindow
import com.kazumaproject.markdownhelperkeyboard.R

internal enum class ImeSelectionPopupPlacement { SCREEN_CENTER, KEYBOARD_CENTER, TOOLBAR_DROPDOWN }

/** Full-screen outside-tap shield and an independently positioned, compact, non-focusable list. */
internal class ImeSelectionPopupWindow(
    context: Context,
    listContent: View,
    placement: ImeSelectionPopupPlacement = ImeSelectionPopupPlacement.SCREEN_CENTER,
    referenceView: View? = null,
    maxVisibleItems: Int = 5,
    referenceViews: List<View> = listOfNotNull(referenceView),
) : PopupWindow() {
    init {
        require(maxVisibleItems > 0)
        val density = context.resources.displayMetrics.density
        val margin = (16 * density).toInt()
        val list = listContent.findViewById<ListView>(R.id.popup_listview)
        list.isVerticalScrollBarEnabled = true
        list.isScrollbarFadingEnabled = false
        val shield = object : FrameLayout(context) {
            private val reference = Rect()
            private var aboveAnchor = false

            private fun referenceBounds(): Boolean {
                reference.setEmpty()
                val origin = IntArray(2).also(::getLocationOnScreen)
                for (target in referenceViews) {
                    val visible = Rect()
                    val globalOffset = Point()
                    if (!target.isAttachedToWindow || !target.isShown || !target.getGlobalVisibleRect(visible, globalOffset)) continue
                    val screen = IntArray(2).also(target::getLocationOnScreen)
                    // Convert each independently hosted view from its root coordinates to this window.
                    visible.offset(screen[0] - globalOffset.x - origin[0], screen[1] - globalOffset.y - origin[1])
                    reference.union(visible)
                }
                return !reference.isEmpty
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val availableWidth = View.MeasureSpec.getSize(widthMeasureSpec)
                val availableHeight = View.MeasureSpec.getSize(heightMeasureSpec)
                list.layoutParams.width = minOf((200 * density).toInt(),
                    (availableWidth - paddingLeft - paddingRight - listContent.paddingLeft - listContent.paddingRight).coerceAtLeast(0))
                val adapter = list.adapter
                var rowsHeight = list.paddingTop + list.paddingBottom
                val count = minOf(maxVisibleItems, adapter?.count ?: 0)
                for (index in 0 until count) {
                    val row = requireNotNull(adapter).getView(index, null, list)
                    row.measure(View.MeasureSpec.makeMeasureSpec(list.layoutParams.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    rowsHeight += row.measuredHeight
                }
                rowsHeight += list.dividerHeight * (count - 1).coerceAtLeast(0)
                val panelPadding = listContent.paddingTop + listContent.paddingBottom
                var panelBudget = (availableHeight - paddingTop - paddingBottom).coerceAtLeast(0)
                if (referenceBounds()) {
                    when (placement) {
                        ImeSelectionPopupPlacement.KEYBOARD_CENTER -> panelBudget = minOf(panelBudget, reference.height())
                        ImeSelectionPopupPlacement.TOOLBAR_DROPDOWN -> {
                            val below = (availableHeight - paddingBottom - reference.bottom).coerceAtLeast(0)
                            val above = (reference.top - paddingTop).coerceAtLeast(0)
                            aboveAnchor = rowsHeight + panelPadding > below && above > below
                            panelBudget = if (aboveAnchor) above else below
                        }
                        else -> Unit
                    }
                }
                val listBudget = (panelBudget - panelPadding).coerceAtLeast(0)
                list.layoutParams.height = minOf(rowsHeight, listBudget)
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }

            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                super.onLayout(changed, left, top, right, bottom)
                val hasReference = referenceBounds()
                var x = (width - listContent.measuredWidth) / 2
                var y = (height - listContent.measuredHeight) / 2
                if (hasReference) {
                    when (placement) {
                        ImeSelectionPopupPlacement.KEYBOARD_CENTER -> {
                            x = reference.centerX() - listContent.measuredWidth / 2
                            y = reference.centerY() - listContent.measuredHeight / 2
                        }
                        ImeSelectionPopupPlacement.TOOLBAR_DROPDOWN -> {
                            x = if (referenceView?.layoutDirection == View.LAYOUT_DIRECTION_RTL)
                                reference.right - listContent.measuredWidth else reference.left
                            y = if (aboveAnchor) reference.top - listContent.measuredHeight else reference.bottom
                        }
                        else -> Unit
                    }
                }
                x = x.coerceIn(paddingLeft, maxOf(paddingLeft, width - paddingRight - listContent.measuredWidth))
                y = y.coerceIn(paddingTop, maxOf(paddingTop, height - paddingBottom - listContent.measuredHeight))
                listContent.layout(x, y, x + listContent.measuredWidth, y + listContent.measuredHeight)
            }
        }.apply {
            setPadding(margin, margin, margin, margin)
            addView(listContent, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            setOnClickListener { dismiss() }
        }
        listContent.isClickable = true
        contentView = shield
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
        isFocusable = false
        isOutsideTouchable = false
        inputMethodMode = INPUT_METHOD_NOT_NEEDED
        // The list already accounts for the visible keyboard. Do not let decor pan it again
        // toward a locally served editor (notably dictionary search on older Android).
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        isAttachedInDecor = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setIsLaidOutInScreen(true)
            setIsClippedToScreen(true)
        } else {
            // Public since API 29; older releases expose the same flag under its former name.
            runCatching {
                PopupWindow::class.java.getDeclaredMethod("setLayoutInScreenEnabled", Boolean::class.javaPrimitiveType)
                    .apply { isAccessible = true }.invoke(this, true)
            }
        }
    }
}
