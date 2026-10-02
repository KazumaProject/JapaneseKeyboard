package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.PopupWindow
import com.kazumaproject.markdownhelperkeyboard.R

/** An IME-owned list must receive taps without taking window focus from the served editor. */
internal class ImeSelectionPopupWindow(context: Context, listContent: View) : PopupWindow() {
    init {
        val margin = (16 * context.resources.displayMetrics.density).toInt()
        val list = listContent.findViewById<ListView>(R.id.popup_listview)
        val preferredListHeight = list.layoutParams.height
        val shield = object : FrameLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                // Long/multiline rows and small landscape windows must still leave a scrollable list.
                val budget = (View.MeasureSpec.getSize(heightMeasureSpec) - paddingTop -
                    paddingBottom - listContent.paddingTop - listContent.paddingBottom).coerceAtLeast(0)
                list.layoutParams.width = minOf(
                    (200 * context.resources.displayMetrics.density).toInt(),
                    (View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight -
                        listContent.paddingLeft - listContent.paddingRight).coerceAtLeast(0),
                )
                list.layoutParams.height = preferredListHeight
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                if (list.measuredHeight > budget) {
                    list.layoutParams.height = budget
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                }
            }
        }.apply {
            setPadding(margin, margin, margin, margin)
            addView(listContent, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER,
            ))
            setOnClickListener { dismiss() }
        }
        // Empty space inside the list container must not trigger the outside-tap action.
        listContent.isClickable = true
        contentView = shield
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
        isFocusable = false
        isOutsideTouchable = false
        inputMethodMode = INPUT_METHOD_NOT_NEEDED
        // IME decor bounds are only the keyboard area. The shield must cover the display.
        isAttachedInDecor = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setIsLaidOutInScreen(true)
            setIsClippedToScreen(true)
        }
    }
}
