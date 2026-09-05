package com.kazumaproject.core.domain.state

import com.kazumaproject.core.domain.key.Key

data class PressedKey(
    var key: Key,
    /** Stable MotionEvent pointer id. This is not a pointer index. */
    var pointerId: Int,
    var initialX: Float,
    var initialY: Float,
)
