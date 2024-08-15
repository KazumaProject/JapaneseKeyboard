package com.kazumaproject.tenkey.state

data class PressedKey(
    var key: Key,
    var pointer: Int,
    var initialX: Float,
    var initialY: Float,
)
