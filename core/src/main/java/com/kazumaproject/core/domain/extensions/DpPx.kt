package com.kazumaproject.core.domain.extensions

import android.content.Context

/**
 * Context 拡張: dp → px（Int 版）
 *
 * @receiver Context
 * @param dp 値（dp単位）
 * @return ピクセル単位で丸めた Int
 */
fun Context.dpToPx(dp: Int): Int =
    (dp * resources.displayMetrics.density).toInt()

/**
 * Context 拡張: dp → px（Float 版）
 *
 * @receiver Context
 * @param dp 値（dp単位）
 * @return ピクセル単位で丸めた Int
 */
fun Context.dpToPx(dp: Float): Int =
    (dp * resources.displayMetrics.density).toInt()

/**
 * Context 拡張: px → dp（Int 版）
 *
 * @receiver Context
 * @param px 値（px単位）
 * @return dp 単位で丸めた Int
 */
fun Context.pxToDp(px: Int): Int =
    (px / resources.displayMetrics.density).toInt()

/**
 * Context 拡張: px → dp（Float 版）
 *
 * @receiver Context
 * @param px 値（px単位）
 * @return dp 単位で丸めた Int
 */
fun Context.pxToDp(px: Float): Int =
    (px / resources.displayMetrics.density).toInt()

/**
 * View 拡張: dp → px（Int 版）
 *
 * View.context を使って dp→px する場合。
 *
 * @receiver View
 * @param dp 値（dp単位）
 * @return ピクセル単位で丸めた Int
 */
fun android.view.View.dpToPx(dp: Int): Int =
    context.dpToPx(dp)

/**
 * View 拡張: px → dp（Int 版）
 *
 * View.context を使って px→dp する場合。
 *
 * @receiver View
 * @param px 値（px単位）
 * @return dp 単位で丸めた Int
 */
fun android.view.View.pxToDp(px: Int): Int =
    context.pxToDp(px)
