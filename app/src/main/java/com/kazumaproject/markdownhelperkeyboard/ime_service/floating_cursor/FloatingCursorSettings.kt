package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor

import android.content.SharedPreferences
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.ComposingGuidePlacement
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.GuideBounds
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.finiteOr

internal class FloatingCursorSettings(private val preferences: SharedPreferences) {
    var enabled: Boolean
        get() = preferences.getBoolean(ENABLED, false)
        set(value) { preferences.edit().putBoolean(ENABLED, value).apply() }

    private fun prefix(landscape: Boolean) = "floating_cursor_${if (landscape) "landscape" else "portrait"}_"
    fun load(landscape: Boolean): ComposingGuidePlacement {
        val p = prefix(landscape)
        return ComposingGuidePlacement(
            preferences.getFloat(p + "x", .5f), preferences.getFloat(p + "y", .1f),
            preferences.getFloat(p + "width", 360f).finiteOr(360f).coerceAtLeast(FloatingCursorView.MIN_WIDTH.toFloat()),
            preferences.getFloat(p + "height", 300f).finiteOr(300f).coerceAtLeast(FloatingCursorView.MIN_HEIGHT.toFloat()))
    }
    fun save(landscape: Boolean, b: GuideBounds, area: GuideBounds, density: Float) {
        val p = prefix(landscape)
        preferences.edit()
            .putFloat(p + "x", (b.x - area.x).toFloat() / (area.width - b.width).coerceAtLeast(1))
            .putFloat(p + "y", (b.y - area.y).toFloat() / (area.height - b.height).coerceAtLeast(1))
            .putFloat(p + "width", b.width / density).putFloat(p + "height", b.height / density).apply()
    }
    fun resetPlacement() {
        preferences.edit().apply {
            listOf(false, true).forEach { landscape ->
                listOf("x", "y", "width", "height").forEach { remove(prefix(landscape) + it) }
            }
            putLong(RESET, preferences.getLong(RESET, 0L) + 1L)
        }.apply()
    }
    companion object {
        const val ENABLED = "floating_cursor_enabled"
        const val RESET = "floating_cursor_reset"
    }
}
