package com.kazumaproject.core.state

import com.kazumaproject.core.key.Key

data class PressedKey(
    var key: Key,
    var pointer: Int,
    var initialX: Float,
    var initialY: Float,
)
