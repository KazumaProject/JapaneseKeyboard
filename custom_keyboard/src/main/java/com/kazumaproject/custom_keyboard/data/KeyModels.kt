package com.kazumaproject.custom_keyboard.data

import androidx.annotation.DrawableRes

// キーボードの見た目ではなく、入力の「モード」を定義する
enum class KeyboardInputMode {
    HIRAGANA,
    ENGLISH,
    SYMBOLS
}

data class KeyData(
    val label: String,
    val row: Int,
    val column: Int,
    val isFlickable: Boolean,
    val isSpecialKey: Boolean = false, // ▼▼▼ 変更 ▼▼▼ 特殊キーかどうかのフラグを追加（デフォルトはfalse）
    val colSpan: Int = 1,
    val rowSpan: Int = 1,
    @DrawableRes val drawableResId: Int? = null
)


data class KeyboardLayout(
    val keys: List<KeyData>,
    val flickKeyMaps: Map<String, List<Map<FlickDirection, String>>>,
    val columnCount: Int,
    val rowCount: Int
)


