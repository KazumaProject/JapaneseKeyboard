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
     * Gets the list of display names for the allowed flick directions.
     * Ideal for populating UI elements like a Spinner.
     * @param context The context needed to access string resources.
     * @return A list of localized direction names.
     */
    fun getDisplayNames(context: Context): List<String> {
        val displayMap = getDirectionDisplayMap(context)
        return allowedDirections.mapNotNull { displayMap[it] }
    }

    /**
     * Finds the FlickDirection enum corresponding to a given display name.
     * @param displayName The localized string (e.g., "タップ").
     * @param context The context needed to access string resources.
     * @return The matching FlickDirection, or null if not found.
     */
    fun fromDisplayName(displayName: String, context: Context): FlickDirection? {
        val displayMap = getDirectionDisplayMap(context)
        return displayMap.entries.firstOrNull { it.value == displayName }?.key
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
