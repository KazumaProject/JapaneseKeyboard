package com.kazumaproject.custom_keyboard.data

fun FlickDirection.toSumireSpecialKeyDirectionOrNull(): SumireSpecialKeyDirection? {
    return when (this) {
        FlickDirection.TAP -> SumireSpecialKeyDirection.TAP
        FlickDirection.UP -> SumireSpecialKeyDirection.UP
        FlickDirection.DOWN -> SumireSpecialKeyDirection.DOWN
        FlickDirection.UP_LEFT,
        FlickDirection.UP_LEFT_FAR -> SumireSpecialKeyDirection.LEFT
        FlickDirection.UP_RIGHT,
        FlickDirection.UP_RIGHT_FAR -> SumireSpecialKeyDirection.RIGHT
    }
}

