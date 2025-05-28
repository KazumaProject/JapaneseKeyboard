package com.kazumaproject.core.domain.extensions

import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout

fun View.setHorizontalWeight(weight: Float) {
    val params = this.layoutParams
    if (params is ConstraintLayout.LayoutParams) {
        params.horizontalWeight = weight
        this.layoutParams = params
    }
}

fun View.setBottomToTopOf(targetView: View) {
    val params = this.layoutParams
    if (params is ConstraintLayout.LayoutParams) {
        params.bottomToTop = targetView.id
        this.layoutParams = params
    }
}

fun View.setEndToStartOf(targetView: View) {
    val params = this.layoutParams
    if (params is ConstraintLayout.LayoutParams) {
        params.endToStart = targetView.id
        this.layoutParams = params
    }
}

fun View.setStartToEndOf(targetView: View) {
    val params = layoutParams as? ConstraintLayout.LayoutParams ?: return
    params.startToEnd = targetView.id
    layoutParams = params
}