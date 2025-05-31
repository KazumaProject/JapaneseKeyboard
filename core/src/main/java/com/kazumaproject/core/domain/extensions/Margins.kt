package com.kazumaproject.core.domain.extensions

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup

fun View.setMarginStart(dp: Float) {
    val px = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics
    ).toInt()
    val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    params.marginStart = px
    layoutParams = params
}

fun View.setMarginEnd(dp: Float) {
    val px = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics
    ).toInt()
    val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    params.marginEnd = px
    layoutParams = params
}