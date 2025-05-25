package com.kazumaproject.core.domain.state

import com.kazumaproject.core.domain.key.Key

data class PressedKey(
    var key: Key,
    var pointer: Int,
    var initialX: Float,
    var initialY: Float,
)
