package com.kazumaproject.core.ui.font

import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan

/** Applies a local keyboard family to text owned by a framework-rendered menu row. */
class KeyboardFontSpan(private val typeface: Typeface) : MetricAffectingSpan() {
    override fun updateMeasureState(textPaint: TextPaint) = apply(textPaint)

    override fun updateDrawState(textPaint: TextPaint) = apply(textPaint)

    private fun apply(textPaint: TextPaint) {
        textPaint.typeface = Typeface.create(typeface, textPaint.typeface?.style ?: Typeface.NORMAL)
    }
}
