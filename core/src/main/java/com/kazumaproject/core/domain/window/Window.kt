package com.kazumaproject.core.domain.window

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.WindowMetrics

fun getScreenHeight(context: Context): Int {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // For Android 11 (API 30) and above
        val windowMetrics: WindowMetrics = windowManager.currentWindowMetrics
        windowMetrics.bounds.height()
    } else {
        // For older versions
        val displayMetrics = DisplayMetrics()
        // getRealMetrics is deprecated but necessary for older APIs
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        displayMetrics.heightPixels
    }
}
