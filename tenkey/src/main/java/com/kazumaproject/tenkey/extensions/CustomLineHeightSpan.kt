package com.kazumaproject.tenkey.extensions

import android.graphics.Paint
import android.text.style.LineHeightSpan

class CustomLineHeightSpan(private val height: Int, private val shift: Int = 0) : LineHeightSpan {
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

            // Adjust ascent and descent to set the custom height
            it.descent += heightDifference / 2
            it.ascent -= heightDifference / 2

            // Apply additional vertical shift if specified
            it.ascent -= shift
            it.descent -= shift
        }
    }
}