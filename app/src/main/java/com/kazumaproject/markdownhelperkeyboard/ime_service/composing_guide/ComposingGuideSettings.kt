package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import android.content.SharedPreferences

internal class ComposingGuideSettings(private val preferences: SharedPreferences) {
    val enabled get() = preferences.getBoolean(ENABLED, false)
    var visible: Boolean
        get() = preferences.getBoolean(VISIBLE, true)
        set(value) { preferences.edit().putBoolean(VISIBLE, value).apply() }
    var textSize: Float
        get() = preferences.getFloat(TEXT_SIZE, 28f).finiteOr(28f).coerceIn(18f, 56f)
        set(value) { preferences.edit().putFloat(TEXT_SIZE, value.coerceIn(18f, 56f)).apply() }

    fun load(landscape: Boolean): ComposingGuidePlacement {
        val prefix = prefix(landscape)
        return ComposingGuidePlacement(
            preferences.getFloat(prefix + "x", .5f), preferences.getFloat(prefix + "y", 1f),
            preferences.getFloat(prefix + "width", 280f), preferences.getFloat(prefix + "height", 112f),
        )
    }

    fun save(landscape: Boolean, placement: ComposingGuidePlacement) {
        val prefix = prefix(landscape)
        preferences.edit().putFloat(prefix + "x", placement.xFraction)
            .putFloat(prefix + "y", placement.yFraction).putFloat(prefix + "width", placement.widthDp)
            .putFloat(prefix + "height", placement.heightDp).apply()
    }

    companion object {
        const val ENABLED = "composing_guide_enabled"
        const val VISIBLE = "composing_guide_visible"
        const val RESET = "composing_guide_reset"
        private const val TEXT_SIZE = "composing_guide_text_size"
        private fun prefix(landscape: Boolean) = "composing_guide_${if (landscape) "landscape" else "portrait"}_"
        fun reset(preferences: SharedPreferences) {
            preferences.edit().apply {
                listOf(false, true).forEach { landscape ->
                    listOf("x", "y", "width", "height").forEach { remove(prefix(landscape) + it) }
                }
                remove(TEXT_SIZE)
            }.apply()
        }
    }
}
