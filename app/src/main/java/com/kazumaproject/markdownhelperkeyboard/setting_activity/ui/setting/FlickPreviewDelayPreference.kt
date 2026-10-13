package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import androidx.preference.SeekBarPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.FlickPreviewDelaySettings

internal fun SeekBarPreference.configureFlickPreviewDelay() {
    value = FlickPreviewDelaySettings.normalize(value)
    setOnPreferenceChangeListener { _, newValue ->
        val raw = newValue as Int
        val normalized = FlickPreviewDelaySettings.normalize(raw)
        if (raw != normalized) {
            // Reject the raw touch value so SeekBarPreference also restores its thumb and label.
            value = normalized
            false
        } else {
            true
        }
    }
}
