package com.kazumaproject.custom_keyboard.data

enum class FlickDirection {
    TAP,
    UP_LEFT_FAR,
    UP_LEFT,
    UP,
    UP_RIGHT,
    UP_RIGHT_FAR,
    DOWN
}

val PETAL_TOGGLE_DIRECTIONS = listOf(
    FlickDirection.TAP,
    FlickDirection.UP_LEFT_FAR,
    FlickDirection.UP,
    FlickDirection.UP_RIGHT_FAR,
    FlickDirection.DOWN
)
