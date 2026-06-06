package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.view.View
import androidx.preference.PreferenceFragmentCompat

internal fun PreferenceFragmentCompat.scrollToHighlightedPreferenceAfterLayout(view: View) {
    arguments
        ?.getString(CommonPreferenceFragment.ARG_HIGHLIGHT_PREFERENCE_KEY)
        ?.takeIf { it.isNotBlank() }
        ?.let { preferenceKey ->
            view.post {
                runCatching {
                    scrollToPreference(preferenceKey)
                }
            }
        }
}
