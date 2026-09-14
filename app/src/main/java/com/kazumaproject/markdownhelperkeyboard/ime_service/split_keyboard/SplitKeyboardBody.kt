package com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard

import android.content.Context
import android.view.View
import android.view.ViewGroup

/** Measures the original body at a usable size; ViewGroup maps touches through its child matrix. */
internal class SplitKeyboardBody(context: Context, private val layoutWidth: Int, private val layoutHeight: Int) : ViewGroup(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        getChildAt(0)?.measure(MeasureSpec.makeMeasureSpec(width.coerceAtLeast(layoutWidth), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height.coerceAtLeast(layoutHeight), MeasureSpec.EXACTLY))
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        getChildAt(0)?.let { body ->
            body.layout(0, 0, body.measuredWidth, body.measuredHeight)
            body.pivotX = 0f; body.pivotY = 0f
            body.scaleX = width.toFloat() / body.measuredWidth.coerceAtLeast(1)
            body.scaleY = height.toFloat() / body.measuredHeight.coerceAtLeast(1)
        }
    }
    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
}
