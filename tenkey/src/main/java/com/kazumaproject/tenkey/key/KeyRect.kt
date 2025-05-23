package com.kazumaproject.tenkey.key

import com.kazumaproject.tenkey.state.Key

data class KeyRect(
    val key: Key,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)
