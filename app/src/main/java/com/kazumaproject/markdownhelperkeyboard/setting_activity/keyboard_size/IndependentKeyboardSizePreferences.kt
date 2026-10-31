package com.kazumaproject.markdownhelperkeyboard.setting_activity.keyboard_size

import android.content.SharedPreferences

/** Size slots for keyboard types that used to share the TenKey-family settings. */
enum class IndependentKeyboardSizeTarget { GOJUON, CUSTOM }

/** Bottom margin is stored in screen pixels by the existing IME. Side margins and height are dp. */
data class IndependentKeyboardSize(
    val heightDp: Int = 220,
    val widthPercent: Int = 100,
    val bottomMarginPx: Int = 0,
    val marginStartDp: Int = 0,
    val marginEndDp: Int = 0,
    val positionEnd: Boolean = true,
)

class IndependentKeyboardSizePreferences(private val preferences: SharedPreferences) {
    fun read(target: IndependentKeyboardSizeTarget, landscape: Boolean): IndependentKeyboardSize {
        migrate(preferences)
        val prefix = prefix(target, landscape)
        return IndependentKeyboardSize(
            heightDp = preferences.int("${prefix}_height", 220),
            widthPercent = preferences.int("${prefix}_width", 100),
            bottomMarginPx = preferences.int("${prefix}_bottom", 0),
            marginStartDp = preferences.int("${prefix}_start", 0),
            marginEndDp = preferences.int("${prefix}_end", 0),
            positionEnd = preferences.bool("${prefix}_position_end", true),
        )
    }

    fun save(target: IndependentKeyboardSizeTarget, landscape: Boolean, value: IndependentKeyboardSize) {
        migrate(preferences)
        val prefix = prefix(target, landscape)
        val previous = read(target, landscape)
        if (previous == value) return
        preferences.edit().apply {
            if (previous.heightDp != value.heightDp) putInt("${prefix}_height", value.heightDp)
            if (previous.widthPercent != value.widthPercent) putInt("${prefix}_width", value.widthPercent)
            if (previous.bottomMarginPx != value.bottomMarginPx) putInt("${prefix}_bottom", value.bottomMarginPx)
            if (previous.marginStartDp != value.marginStartDp) putInt("${prefix}_start", value.marginStartDp)
            if (previous.marginEndDp != value.marginEndDp) putInt("${prefix}_end", value.marginEndDp)
            if (previous.positionEnd != value.positionEnd) putBoolean("${prefix}_position_end", value.positionEnd)
        }.apply()
    }

    companion object {
        const val KEY_PREFIX = "independent_keyboard_size_"
        private const val MIGRATED_KEY = "independent_keyboard_size_migrated"

        private fun prefix(target: IndependentKeyboardSizeTarget, landscape: Boolean) =
            "$KEY_PREFIX${target.name.lowercase()}_${if (landscape) "landscape" else "portrait"}"

        /** Snapshot legacy values once before either family can be edited independently. */
        @Synchronized
        fun migrate(preferences: SharedPreferences) {
            if (preferences.bool(MIGRATED_KEY, false)) return
            val edit = preferences.edit()
            for (landscape in listOf(false, true)) {
                val suffix = if (landscape) "_landscape_preference" else "_preference"
                val legacy = IndependentKeyboardSize(
                    heightDp = preferences.int("keyboard_height$suffix", 220),
                    widthPercent = preferences.int("keyboard_width$suffix", 100),
                    bottomMarginPx = preferences.int("keyboard_vertical_margin_bottom$suffix", 0),
                    marginStartDp = preferences.int(if (landscape) "keyboard_margin_start_dp_landscape_preference" else "keyboard_margin_start_dp_preference", 0),
                    marginEndDp = preferences.int(if (landscape) "keyboard_margin_end_dp_landscape_preference" else "keyboard_margin_end_dp_preference", 0),
                    positionEnd = preferences.bool("keyboard_position$suffix", true),
                )
                for (target in IndependentKeyboardSizeTarget.entries) {
                    val prefix = prefix(target, landscape)
                    fun putIntIfAbsent(name: String, value: Int) {
                        val key = "${prefix}_$name"
                        if (!preferences.contains(key)) edit.putInt(key, value)
                    }
                    putIntIfAbsent("height", legacy.heightDp)
                    putIntIfAbsent("width", legacy.widthPercent)
                    putIntIfAbsent("bottom", legacy.bottomMarginPx)
                    putIntIfAbsent("start", legacy.marginStartDp)
                    putIntIfAbsent("end", legacy.marginEndDp)
                    if (!preferences.contains("${prefix}_position_end")) edit.putBoolean("${prefix}_position_end", legacy.positionEnd)
                }
            }
            edit.putBoolean(MIGRATED_KEY, true).apply()
        }
    }
}

private fun SharedPreferences.int(key: String, default: Int): Int =
    runCatching { getInt(key, default) }.getOrDefault(default)

private fun SharedPreferences.bool(key: String, default: Boolean): Boolean =
    runCatching { getBoolean(key, default) }.getOrDefault(default)
