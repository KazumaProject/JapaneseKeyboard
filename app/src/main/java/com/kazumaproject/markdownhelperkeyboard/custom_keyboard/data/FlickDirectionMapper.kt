package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data

import android.content.Context
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.markdownhelperkeyboard.R

import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection

object FlickDirectionMapper {

    // This list remains the same as it defines the logic, not the UI.
    val allowedDirections = listOf(
        FlickDirection.TAP,
        FlickDirection.UP_LEFT_FAR,
        FlickDirection.UP,
        FlickDirection.UP_RIGHT_FAR,
        FlickDirection.DOWN
    )

    /**
     * Gets the display name for a given FlickDirection enum.
     */
    fun toDisplayName(direction: FlickDirection, context: Context): String {
        return when (direction) {
            FlickDirection.TAP -> context.getString(R.string.tap)
            FlickDirection.UP_LEFT_FAR -> context.getString(R.string.flick_left)
            FlickDirection.UP_LEFT -> context.getString(R.string.flick_left)
            FlickDirection.UP -> context.getString(R.string.flick_top)
            FlickDirection.UP_RIGHT -> context.getString(R.string.flick_right)
            FlickDirection.UP_RIGHT_FAR -> context.getString(R.string.flick_right)
            FlickDirection.DOWN -> context.getString(R.string.flick_bottom)
        }
    }

    /**
     * Gets the display name for a given TfbiFlickDirection enum.
     */
    fun toDisplayName(direction: TfbiFlickDirection, context: Context): String {
        return when (direction) {
            TfbiFlickDirection.TAP -> context.getString(R.string.tap)
            TfbiFlickDirection.UP -> context.getString(R.string.flick_top)
            TfbiFlickDirection.DOWN -> context.getString(R.string.flick_bottom)
            TfbiFlickDirection.LEFT -> context.getString(R.string.flick_left)
            TfbiFlickDirection.RIGHT -> context.getString(R.string.flick_right)
            TfbiFlickDirection.UP_LEFT -> context.getString(R.string.flick_up_left)
            TfbiFlickDirection.UP_RIGHT -> context.getString(R.string.flick_up_right)
            TfbiFlickDirection.DOWN_LEFT -> context.getString(R.string.flick_down_left)
            TfbiFlickDirection.DOWN_RIGHT -> context.getString(R.string.flick_down_right)
        }
    }
}
