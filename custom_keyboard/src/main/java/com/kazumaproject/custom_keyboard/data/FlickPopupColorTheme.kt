package com.kazumaproject.custom_keyboard.data

import androidx.annotation.ColorInt

/**
 * FlickCirclePopupViewの配色を定義するデータクラス
 * ▼▼▼ グラデーションに対応 ▼▼▼
 */
data class FlickPopupColorTheme(
    // フリック先セグメントの通常時の色 (単色)
    @ColorInt val segmentColor: Int,

    // フリック先セグメントのハイライト時のグラデーション
    @ColorInt val segmentHighlightGradientStartColor: Int,
    @ColorInt val segmentHighlightGradientEndColor: Int,

    // 中央の円の通常時のグラデーション
    @ColorInt val centerGradientStartColor: Int,
    @ColorInt val centerGradientEndColor: Int,

    // 中央の円のハイライト時のグラデーション
    @ColorInt val centerHighlightGradientStartColor: Int,
    @ColorInt val centerHighlightGradientEndColor: Int,

    // 区切り線の色 (単色)
    @ColorInt val separatorColor: Int,
    // 文字の色 (単色)
    @ColorInt val textColor: Int
)
