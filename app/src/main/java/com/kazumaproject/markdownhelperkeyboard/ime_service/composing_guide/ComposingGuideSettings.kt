package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import android.content.SharedPreferences

internal enum class GuideProfile(val key: String, val hasText: Boolean, val hasCandidates: Boolean) {
    INTEGRATED("integrated", true, true),
    CANDIDATES("candidates", false, true),
    TEXT("text", true, false),
}

internal class ComposingGuideSettings(private val preferences: SharedPreferences) {
    init { migrate(preferences) }
    val showReading get() = preferences.getBoolean(SHOW_READING, false)
    val textEnabled get() = preferences.getBoolean(TEXT_ENABLED, false)
    val enabled get() = preferences.getBoolean(ENABLED, false)
    val combined get() = preferences.getString(DISPLAY_MODE, "integrated") != "separate"
    val verticalCandidates get() = preferences.getString(SCROLL_DIRECTION, "vertical") == "vertical"
    val profiles: List<GuideProfile> get() = when {
        enabled && textEnabled && combined -> listOf(GuideProfile.INTEGRATED)
        else -> buildList {
            if (enabled) add(GuideProfile.CANDIDATES)
            if (textEnabled) add(GuideProfile.TEXT)
        }
    }
    var textSize: Float
        get() = preferences.getFloat(TEXT_SIZE, 28f).finiteOr(28f).coerceIn(18f, 56f)
        set(value) { preferences.edit().putFloat(TEXT_SIZE, value.coerceIn(18f, 56f)).apply() }

    fun load(landscape: Boolean, profile: GuideProfile = GuideProfile.INTEGRATED): ComposingGuidePlacement {
        val prefix = prefix(landscape, profile)
        return ComposingGuidePlacement(
            preferences.getFloat(prefix + "x", .5f), preferences.getFloat(prefix + "y", 1f),
            preferences.getFloat(prefix + "width", 280f), preferences.getFloat(prefix + "height", 264f),
        )
    }

    fun prepareCandidatePlacement(landscape: Boolean) {
        val target = prefix(landscape, GuideProfile.CANDIDATES)
        if (preferences.getBoolean(target + "initialized", false)) return
        val source = prefix(landscape, GuideProfile.INTEGRATED)
        preferences.edit().apply {
            geometryKeys.forEach { suffix ->
                if (preferences.contains(source + suffix)) {
                    if (suffix == "screen_coordinates") putBoolean(target + suffix, preferences.getBoolean(source + suffix, false))
                    else putFloat(target + suffix, preferences.getFloat(source + suffix, 0f))
                }
            }
            putBoolean(target + "initialized", true)
        }.apply()
    }

    fun usesScreenCoordinates(landscape: Boolean, profile: GuideProfile = GuideProfile.INTEGRATED) =
        preferences.getBoolean(prefix(landscape, profile) + "screen_coordinates", false)

    fun save(landscape: Boolean, placement: ComposingGuidePlacement, profile: GuideProfile = GuideProfile.INTEGRATED) {
        val prefix = prefix(landscape, profile)
        preferences.edit().putBoolean(prefix + "screen_coordinates", true).putFloat(prefix + "x", placement.xFraction)
            .putFloat(prefix + "y", placement.yFraction).putFloat(prefix + "width", placement.widthDp)
            .putFloat(prefix + "height", placement.heightDp).apply()
    }

    companion object {
        const val SHOW_READING = "composing_guide_show_live_reading"
        const val SHOW_COMPOSING = "composing_guide_show_composing" // Legacy migration only.
        const val TEXT_ENABLED = "composing_guide_text_enabled"
        const val DISPLAY_MODE = "composing_guide_display_mode"
        const val SCROLL_DIRECTION = "composing_guide_scroll_direction"
        const val ENABLED = "composing_guide_enabled"
        const val TEXT_SIZE = "composing_guide_text_size"
        private const val MIGRATED = "composing_guide_independent_migrated"
        private val geometryKeys = listOf("x", "y", "width", "height", "screen_coordinates")
        private fun prefix(landscape: Boolean, profile: GuideProfile) =
            "composing_guide_${if (profile == GuideProfile.INTEGRATED) "" else profile.key + "_"}${if (landscape) "landscape" else "portrait"}_"

        fun migrate(preferences: SharedPreferences) {
            if (preferences.getBoolean(MIGRATED, false)) return
            preferences.edit().apply {
                if (!preferences.contains(TEXT_ENABLED)) putBoolean(TEXT_ENABLED,
                    preferences.getBoolean(ENABLED, false) && preferences.getBoolean(SHOW_COMPOSING, true))
                if (!preferences.contains(DISPLAY_MODE)) putString(DISPLAY_MODE, "integrated")
                listOf(false, true).forEach { landscape ->
                    geometryKeys.forEach { suffix ->
                        val oldKey = prefix(landscape, GuideProfile.INTEGRATED) + suffix
                        val newKey = prefix(landscape, GuideProfile.CANDIDATES) + suffix
                        if (preferences.contains(oldKey) && !preferences.contains(newKey)) {
                            if (suffix == "screen_coordinates") putBoolean(newKey, preferences.getBoolean(oldKey, false))
                            else putFloat(newKey, preferences.getFloat(oldKey, 0f))
                        }
                    }
                }
                putBoolean(MIGRATED, true)
            }.apply()
        }

        fun reset(preferences: SharedPreferences, profile: GuideProfile = GuideProfile.INTEGRATED) {
            preferences.edit().apply {
                listOf(false, true).forEach { landscape ->
                    geometryKeys.forEach { remove(prefix(landscape, profile) + it) }
                    if (profile == GuideProfile.CANDIDATES) putBoolean(prefix(landscape, profile) + "initialized", true)
                }
            }.apply()
        }
    }
}
