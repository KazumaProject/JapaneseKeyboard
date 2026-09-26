package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.PhysicalToolbarSettings
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HardwareKeyboardPreferenceFragment : PreferenceFragmentCompat() {
    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                findPreference<SwitchPreferenceCompat>(PhysicalToolbarSettings.VOICE_KEY)
                    ?.isChecked = true
            } else if (isAdded) {
                Toast.makeText(
                    requireContext(),
                    R.string.physical_toolbar_voice_permission_denied,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_hardware_keyboard, rootKey)
        val style = findPreference<ListPreference>(PhysicalToolbarSettings.STYLE_KEY)
        val mode = findPreference<SwitchPreferenceCompat>(PhysicalToolbarSettings.MODE_KEY)
        val keyboard = findPreference<SwitchPreferenceCompat>(PhysicalToolbarSettings.KEYBOARD_KEY)
        val voice = findPreference<SwitchPreferenceCompat>(PhysicalToolbarSettings.VOICE_KEY)
        val items = listOfNotNull(mode, keyboard, voice)
        if (items.isNotEmpty() && items.none { it.isChecked }) {
            mode?.isChecked = true
        }

        fun updateItemVisibility(value: String?) {
            items.forEach { it.isVisible = value == "floating" }
        }
        updateItemVisibility(style?.value)
        style?.setOnPreferenceChangeListener { _, value ->
            updateItemVisibility(value as? String)
            true
        }
        items.forEach { item ->
            item.setOnPreferenceChangeListener { _, value ->
                val enabled = value as Boolean
                if (!enabled && items.none { it !== item && it.isChecked }) {
                    Toast.makeText(
                        requireContext(),
                        R.string.physical_toolbar_keep_one,
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@setOnPreferenceChangeListener false
                }
                if (item === voice && enabled &&
                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.RECORD_AUDIO,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    return@setOnPreferenceChangeListener false
                }
                true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        findPreference<Preference>("physical_keyboard_shortcut_setting_preference")
            ?.setOnPreferenceClickListener {
                navigateSafely(R.id.physicalKeyboardShortcutListFragment)
                true
            }
        applyLegacySearchResultFilterIfNeeded()
    }
}
