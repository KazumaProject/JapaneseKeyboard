package com.kazumaproject.markdownhelperkeyboard.gesture

import android.os.Bundle
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.kazumaproject.gesture_keyboard.GestureSide
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.AsyncPreferenceFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.applyLegacySearchResultFilterIfNeeded
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.navigateSafely
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GesturePreferenceFragment : AsyncPreferenceFragment() {
    override val preferencesXmlRes = R.xml.pref_gesture
    override fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) {
        findPreference<Preference>("gesture_controls")?.setOnPreferenceClickListener { navigateSafely(R.id.gestureLayoutFragment); true }
        findPreference<Preference>("gesture_sets")?.setOnPreferenceClickListener { navigateSafely(R.id.gestureSetListFragment); true }
        refreshSets()
        applyLegacySearchResultFilterIfNeeded()
    }
    override fun onPreferencesResumed() { refreshSets() }
    private fun refreshSets() {
        val store = GesturePreferences(requireContext())
        val sets = store.sets()
        GestureSide.entries.forEach { side ->
            findPreference<ListPreference>(if (side == GestureSide.LEFT) "gesture_left_set" else "gesture_right_set")?.apply {
                isPersistent = false
                entries = sets.map { it.name }.toTypedArray(); entryValues = sets.map { it.id }.toTypedArray()
                value = store.session().setId(side)
                setOnPreferenceChangeListener { _, id ->
                    runCatching { store.saveSession(store.session().withSet(side, id as String)) }
                        .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }.isSuccess
                }
            }
        }
        if (store.invalidData()) Toast.makeText(requireContext(), R.string.gesture_invalid_data, Toast.LENGTH_LONG).show()
    }
}
