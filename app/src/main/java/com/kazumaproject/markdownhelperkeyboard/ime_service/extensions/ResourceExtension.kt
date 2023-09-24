package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

import android.content.Context
import android.util.DisplayMetrics


/**
 * dp -> pixel in int
 * @param context
 * @return int pixel
 */
fun Float.convertDp2Px(context: Context): Int {
    val metrics: DisplayMetrics = context.resources.displayMetrics
    return (this * metrics.density).toInt()
}

/**
 * dp -> pixel in float
 * @param context
 * @return float pixel
 */
fun Float.convertDp2PxFloat(context: Context): Float {
    val metrics: DisplayMetrics = context.resources.displayMetrics
    return this * metrics.density
}

/**
 * pixel -> dp
 * @param context
 * @return int dp
 */
fun Float.convertPx2Dp(context: Context): Int {
    val metrics: DisplayMetrics = context.resources.displayMetrics
    return (this / metrics.density).toInt()
}


/**
 * pixel -> sp
 * @param context
 * @return float sp
 */
fun Float.convertPx2Sp(context: Context): Float {
    val scaledDensity = context.resources.displayMetrics.scaledDensity
    return this / scaledDensity
}

/**
 * sp -> pixel
 * @param context
 * @return float pixel
 */
fun Float.convertSp2Px(context: Context): Float {
    val metrics: Float = context.resources.displayMetrics.scaledDensity
    return this * metrics
}