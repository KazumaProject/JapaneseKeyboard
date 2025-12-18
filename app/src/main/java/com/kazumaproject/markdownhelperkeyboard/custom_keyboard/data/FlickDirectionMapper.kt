package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data

import android.content.Context
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.markdownhelperkeyboard.R

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
     * Creates a map of FlickDirection enums to their display names using string resources.
     * @param context The context needed to access string resources.
     * @return A map where keys are FlickDirection enums and values are their localized names.
     */
    private fun getDirectionDisplayMap(context: Context): Map<FlickDirection, String> {
        return mapOf(
            FlickDirection.TAP to context.getString(com.kazumaproject.custom_keyboard.R.string.tap),
            FlickDirection.UP_LEFT_FAR to context.getString(R.string.flick_left),
            FlickDirection.UP to context.getString(R.string.flick_top),
            FlickDirection.UP_RIGHT_FAR to context.getString(R.string.flick_right),
            FlickDirection.DOWN to context.getString(R.string.flick_bottom)
        )
    }

    /**
     * Gets the display name for a given FlickDirection enum.
     * @param direction The FlickDirection enum.
     * @param context The context needed to access string resources.
     * @return The localized name, or null if the direction is not mapped.
     */
    fun toDisplayName(direction: FlickDirection, context: Context): String? {
        return getDirectionDisplayMap(context)[direction]
    }
}
