package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import androidx.core.os.bundleOf
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import com.kazumaproject.markdownhelperkeyboard.R

/** Redirect old purpose-specific entries to the category that owns the setting. */
internal fun PreferenceFragmentCompat.bindCanonicalSettingLinks() {
    fun visit(group: PreferenceGroup) {
        for (index in 0 until group.preferenceCount) {
            val preference = group.getPreference(index)
            val category = preference.key?.removePrefix("setting_route_canonical_")
                ?.takeIf { preference.key != it }
            if (category != null) {
                val destination = when {
                    category.startsWith("settings_common_") -> R.id.legacyCommonPreferenceFragment
                    category.startsWith("settings_theme_") -> R.id.keyboardThemeFragment
                    category.startsWith("settings_engine_") -> R.id.conversionEnginePreferenceFragment
                    else -> continue
                }
                preference.setOnPreferenceClickListener {
                    navigateSafely(destination, bundleOf(CommonPreferenceFragment.ARG_HIGHLIGHT_PREFERENCE_KEY to category))
                    true
                }
            }
            if (preference is PreferenceGroup) visit(preference)
        }
    }
    preferenceScreen?.let(::visit)
}
