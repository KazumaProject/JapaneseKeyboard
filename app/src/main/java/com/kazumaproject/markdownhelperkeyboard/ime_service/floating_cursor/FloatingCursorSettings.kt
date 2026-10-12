package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor

import android.content.SharedPreferences
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.ComposingGuidePlacement

internal class FloatingCursorSettings(private val preferences: SharedPreferences) {
    val enabled get() = preferences.getBoolean(ENABLED, false)

    fun load(landscape: Boolean) = ComposingGuidePlacement(
        preferences.getFloat(prefix(landscape) + "x", .5f),
        preferences.getFloat(prefix(landscape) + "y", .25f),
        preferences.getFloat(prefix(landscape) + "width", 320f),
        preferences.getFloat(prefix(landscape) + "height", 300f),
    )

    fun save(landscape: Boolean, placement: ComposingGuidePlacement) {
        preferences.edit().apply {
            putFloat(prefix(landscape) + "x", placement.xFraction)
            putFloat(prefix(landscape) + "y", placement.yFraction)
            putFloat(prefix(landscape) + "width", placement.widthDp)
            putFloat(prefix(landscape) + "height", placement.heightDp)
        }.apply()
    }

    companion object {
        const val ENABLED = "floating_cursor_enabled"
        const val RESET = "floating_cursor_reset"
        private fun prefix(landscape: Boolean) = "floating_cursor_${if (landscape) "landscape" else "portrait"}_"
        fun reset(preferences: SharedPreferences) {
            preferences.edit().apply {
                listOf(false, true).forEach { landscape ->
                    listOf("x", "y", "width", "height").forEach { remove(prefix(landscape) + it) }
                }
            }.apply()
        }
    }
}
