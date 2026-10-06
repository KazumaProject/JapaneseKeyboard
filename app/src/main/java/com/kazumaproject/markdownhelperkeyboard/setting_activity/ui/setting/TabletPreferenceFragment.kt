package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import com.kazumaproject.markdownhelperkeyboard.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TabletPreferenceFragment : AsyncPreferenceFragment() {
    override val preferencesXmlRes: Int = R.xml.pref_tablet

    override fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) {
        applyLegacySearchResultFilterIfNeeded()
    }
}
