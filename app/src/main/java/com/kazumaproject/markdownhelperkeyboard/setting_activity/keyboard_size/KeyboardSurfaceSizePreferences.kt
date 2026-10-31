package com.kazumaproject.markdownhelperkeyboard.setting_activity.keyboard_size

import android.content.SharedPreferences

/** The Sumire and TenKey surfaces deliberately share the legacy TenKey slot. */
enum class KeyboardSurfaceSizeTarget { TENKEY, QWERTY, GOJUON, CUSTOM }

class KeyboardSurfaceSizePreferences(private val preferences: SharedPreferences) {
    private val independent = IndependentKeyboardSizePreferences(preferences)

    fun read(target: KeyboardSurfaceSizeTarget, landscape: Boolean): IndependentKeyboardSize {
        independentTarget(target)?.let { return independent.read(it, landscape) }
        val keys = legacyKeys(target, landscape)
        return IndependentKeyboardSize(
            heightDp = preferences.int(keys.height, 220),
            widthPercent = preferences.int(keys.width, 100),
            bottomMarginPx = preferences.int(keys.bottom, 0),
            marginStartDp = preferences.int(keys.start, 0),
            marginEndDp = preferences.int(keys.end, 0),
            positionEnd = preferences.bool(keys.position, true),
        )
    }

    fun save(target: KeyboardSurfaceSizeTarget, landscape: Boolean, value: IndependentKeyboardSize) {
        independentTarget(target)?.let { independent.save(it, landscape, value); return }
        val keys = legacyKeys(target, landscape)
        val previous = read(target, landscape)
        if (previous == value) return
        preferences.edit().apply {
            if (previous.heightDp != value.heightDp) putInt(keys.height, value.heightDp)
            if (previous.widthPercent != value.widthPercent) putInt(keys.width, value.widthPercent)
            if (previous.bottomMarginPx != value.bottomMarginPx) putInt(keys.bottom, value.bottomMarginPx)
            if (previous.marginStartDp != value.marginStartDp) putInt(keys.start, value.marginStartDp)
            if (previous.marginEndDp != value.marginEndDp) putInt(keys.end, value.marginEndDp)
            if (previous.positionEnd != value.positionEnd) putBoolean(keys.position, value.positionEnd)
        }.apply()
    }

    companion object {
        private data class Keys(val height: String, val width: String, val bottom: String,
            val start: String, val end: String, val position: String)

        private fun independentTarget(target: KeyboardSurfaceSizeTarget) = when (target) {
            KeyboardSurfaceSizeTarget.GOJUON -> IndependentKeyboardSizeTarget.GOJUON
            KeyboardSurfaceSizeTarget.CUSTOM -> IndependentKeyboardSizeTarget.CUSTOM
            else -> null
        }

        private fun legacyKeys(target: KeyboardSurfaceSizeTarget, landscape: Boolean): Keys {
            val base = if (target == KeyboardSurfaceSizeTarget.QWERTY) "qwerty_keyboard" else "keyboard"
            val suffix = if (landscape) "_landscape_preference" else "_preference"
            return Keys("${base}_height$suffix", "${base}_width$suffix", "${base}_vertical_margin_bottom$suffix",
                "${base}_margin_start_dp$suffix", "${base}_margin_end_dp$suffix", "${base}_position$suffix")
        }

        fun isLegacySizeKey(key: String): Boolean = listOf(false, true).any { landscape ->
            listOf(KeyboardSurfaceSizeTarget.TENKEY, KeyboardSurfaceSizeTarget.QWERTY).any { target ->
                val keys = legacyKeys(target, landscape)
                key in listOf(keys.height, keys.width, keys.bottom, keys.start, keys.end, keys.position)
            }
        }
    }
}

private fun SharedPreferences.int(key: String, default: Int) = runCatching { getInt(key, default) }.getOrDefault(default)
private fun SharedPreferences.bool(key: String, default: Boolean) = runCatching { getBoolean(key, default) }.getOrDefault(default)
