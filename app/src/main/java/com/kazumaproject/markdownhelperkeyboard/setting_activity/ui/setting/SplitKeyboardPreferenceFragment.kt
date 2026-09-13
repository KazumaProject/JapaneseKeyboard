package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_selection.getKeyboardDisplayName
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SplitKeyboardPreferenceFragment : PreferenceFragmentCompat() {
    @Inject lateinit var repository: KeyboardRepository
    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scrollToHighlightedPreferenceAfterLayout(view)
    }
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_split_keyboard, rootKey)
        val types = KeyboardType.entries.filter { it != KeyboardType.SPLIT }
        SplitSlot.entries.forEach { slot ->
            val custom = findPreference<ListPreference>(SplitKeyboardSettings.customKey(slot))!!
            findPreference<ListPreference>(SplitKeyboardSettings.typeKey(slot))!!.apply {
                entries = types.map { requireContext().getKeyboardDisplayName(it) }.toTypedArray()
                entryValues = types.map { it.name }.toTypedArray()
                custom.isVisible = value == KeyboardType.CUSTOM.name
                setOnPreferenceChangeListener { _, value -> custom.isVisible = value == KeyboardType.CUSTOM.name; true }
            }
        }
        findPreference<ListPreference>(SplitKeyboardSettings.CANDIDATES)!!.apply {
            entries = arrayOf(getString(R.string.split_keyboard_both), getString(R.string.split_keyboard_main_only), getString(R.string.split_keyboard_sub_only))
            entryValues = SplitCandidatePlacement.entries.map { it.name }.toTypedArray()
        }
        lifecycleScope.launch {
            repository.getLayoutsNotFlowEnsuringStableIds()
            repository.getLayouts().collect { layouts ->
                SplitSlot.entries.forEach { slot ->
                    findPreference<ListPreference>(SplitKeyboardSettings.customKey(slot))?.apply {
                        entries = layouts.map { it.name }.toTypedArray()
                        entryValues = layouts.map { it.stableId }.toTypedArray()
                        isEnabled = layouts.isNotEmpty()
                    }
                }
            }
        }
    }
}
