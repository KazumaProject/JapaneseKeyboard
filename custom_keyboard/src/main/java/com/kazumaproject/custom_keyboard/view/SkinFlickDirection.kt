package com.kazumaproject.custom_keyboard.view

import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.core.ui.skin.PopupDirection

internal fun FlickDirection.skinDirection(): PopupDirection = when (this) {
    FlickDirection.TAP -> PopupDirection.CENTER
    FlickDirection.UP -> PopupDirection.TOP
    FlickDirection.DOWN -> PopupDirection.BOTTOM
    FlickDirection.UP_LEFT, FlickDirection.UP_LEFT_FAR -> PopupDirection.LEFT
    FlickDirection.UP_RIGHT, FlickDirection.UP_RIGHT_FAR -> PopupDirection.RIGHT
}
