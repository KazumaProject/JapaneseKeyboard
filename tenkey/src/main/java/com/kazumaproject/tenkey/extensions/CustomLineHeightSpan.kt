package com.kazumaproject.tenkey.extensions

import android.graphics.Paint
import android.text.style.LineHeightSpan

class CustomLineHeightSpan(private val height: Int) : LineHeightSpan {
    override fun chooseHeight(
        text: CharSequence?,
        start: Int,
        end: Int,
        spanstartv: Int,
        v: Int,
        fm: Paint.FontMetricsInt?
    ) {
        fm?.let {
            val originalHeight = it.descent - it.ascent
            val heightDifference = height - originalHeight
            it.descent += heightDifference / 2
            it.ascent -= heightDifference / 2
        }
    }
}