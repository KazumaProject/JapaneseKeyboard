package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.os.LocaleListCompat
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.color.colorChooser
import com.google.android.material.color.DynamicColors
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var appPreference: AppPreference

    @Inject
    lateinit var userDictionaryRepository: UserDictionaryRepository

    @Inject
    lateinit var kanaKanjiEngine: KanaKanjiEngine

    private var count = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isTablet = resources.getBoolean(com.kazumaproject.core.R.bool.isTablet)
        if (isTablet) {
            appPreference.flick_input_only_preference = true
        }
        CoroutineScope(Dispatchers.IO).launch {
            userDictionaryRepository.apply {
                if (searchByReadingExactMatchSuspend("びゃんびゃんめん").isEmpty()) {
                    insert(
                        UserWord(
                            reading = "びゃんびゃんめん",
                            word = "\uD883\uDEDE\uD883\uDEDE麺",
                            posIndex = 0,
                            posScore = 4000
                        )
                    )
                }
                if (searchByReadingExactMatchSuspend("びゃん").isEmpty()) {
                    insert(
                        UserWord(
                            reading = "びゃん", word = "\uD883\uDEDE", posIndex = 0, posScore = 3000
                        )
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                requireActivity().finish()
            }
        }
        isKeyboardBoardEnabled()?.let { enabled ->
            if (!enabled) {
                goToKeyboardSettingScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isKeyboardBoardEnabled()?.let { enabled ->
            if (!enabled) {
                goToKeyboardSettingScreen()
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.setting_preference, rootKey)

        val packageInfo = requireContext().packageManager.getPackageInfo(
            requireContext().packageName, 0
        )

        val languageSwitchPreference =
            findPreference<SwitchPreferenceCompat>("app_setting_language_preference")
        languageSwitchPreference?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                val state = newValue as Boolean
                if (state) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ja"))
                } else {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                }
                true
            }
        }

        val sumireStylePreference =
            findPreference<ListPreference>("sumire_keyboard_style_preference")
        sumireStylePreference?.apply {
            val originalEntries = this.entries
            val originalEntryValues = this.entryValues
            setOnPreferenceClickListener {
                if (count >= 5) {
                    entries = originalEntries
                    entryValues = originalEntryValues
                } else {
                    entries = originalEntries.dropLast(1).toTypedArray()
                    entryValues = originalEntryValues.dropLast(1).toTypedArray()
                }
                return@setOnPreferenceClickListener true
            }
            summary = entries[findIndexOfValue(value)].toString()
            setOnPreferenceChangeListener { preference, newValue ->
                val listPreference = preference as ListPreference
                val index = listPreference.findIndexOfValue(newValue as String)
                if (index >= 0) {
                    preference.summary = listPreference.entries[index].toString()
                }
                true
            }
        }

        val sumireMethodPreference =
            findPreference<ListPreference>("sumire_input_method_preference")
        sumireMethodPreference?.apply {
            summary = entries[findIndexOfValue(value)].toString() // 現在の選択肢をサマリーに表示
            setOnPreferenceChangeListener { preference, newValue ->
                val listPreference = preference as ListPreference
                val index = listPreference.findIndexOfValue(newValue as String)
                if (index >= 0) {
                    preference.summary = listPreference.entries[index].toString()
                }
                true
            }
        }

        val appVersionPreference = findPreference<Preference>("app_version_preference")
        appVersionPreference?.apply {
            summary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                "version name: ${packageInfo.versionName}\nversion code: ${packageInfo.longVersionCode}"
            } else {
                "version name: ${packageInfo.versionName}\nversion code: ${packageInfo.versionCode}"
            }
            setOnPreferenceClickListener {
                count += 1
                true
            }
        }

        val keyboardSelectionPreference =
            findPreference<Preference>("keyboard_selection_preference")

        keyboardSelectionPreference?.setOnPreferenceClickListener {
            findNavController().navigate(
                R.id.action_navigation_setting_to_keyboardSelectionFragment
            )
            true
        }

        val keyboardLetterSizePreference =
            findPreference<Preference>("keyboard_key_letter_size_fragment_preference")

        keyboardLetterSizePreference?.setOnPreferenceClickListener {
            findNavController().navigate(
                R.id.action_navigation_setting_to_keyCandidateLetterSizeFragment
            )
            true
        }

        val clipBoardHistoryPreference =
            findPreference<Preference>("clipboard_history_preference_fragment")
        clipBoardHistoryPreference?.apply {
            setOnPreferenceClickListener {
                findNavController().navigate(
                    R.id.action_navigation_setting_to_clipboardHistoryFragment
                )
                true
            }
        }

        val symbolKeyboardOrderPreference = findPreference<ListPreference>("symbol_mode_preference")
        symbolKeyboardOrderPreference?.apply {
            summary = when (value) {
                "EMOJI" -> getString(R.string.emoji)
                "EMOTICON" -> getString(R.string.emoticon)
                "SYMBOL" -> getString(R.string.symbol)
                "CLIPBOARD" -> getString(R.string.clipboard_history)
                else -> getString(R.string.choose_initial_symbol_keyboard_open)
            }
            setOnPreferenceChangeListener { _, newValue ->
                summary = when (newValue) {
                    "EMOJI" -> getString(R.string.emoji)
                    "EMOTICON" -> getString(R.string.emoticon)
                    "SYMBOL" -> getString(R.string.symbol)
                    "CLIPBOARD" -> getString(R.string.clipboard_history)
                    else -> getString(R.string.choose_initial_symbol_keyboard_open)
                }
                true
            }
        }

        val ngWordSwitchPreference =
            findPreference<SwitchPreferenceCompat>("ng_word_enable_preference")
        ngWordSwitchPreference?.apply {
            title = if (isChecked) {
                getString(R.string.ng_word_enable_title_on)
            } else {
                getString(R.string.ng_word_enable_title_off)
            }
            setOnPreferenceChangeListener { _, newValue ->
                title = if (newValue == true) {
                    getString(R.string.ng_word_enable_title_on)
                } else {
                    getString(R.string.ng_word_enable_title_off)
                }
                true
            }
        }

        val ngWordPreference = findPreference<Preference>("ng_word_preference")
        ngWordPreference?.setOnPreferenceClickListener {
            findNavController().navigate(
                R.id.action_navigation_setting_to_ngWordFragment
            )
            true
        }

        val userDictionaryPrefixSeekBar =
            findPreference<SeekBarPreference>("user_dictionary_prefix_match_number")
        userDictionaryPrefixSeekBar?.apply {
            appPreference.user_dictionary_prefix_match_number_preference?.let {
                this.summary =
                    resources.getString(R.string.user_dictionary_prefix_match_summary, it)
            }
            this.setOnPreferenceChangeListener { _, newValue ->
                this.summary =
                    resources.getString(
                        R.string.user_dictionary_prefix_match_summary,
                        newValue as Int
                    )
                true
            }
        }

        findPreference<SeekBarPreference>("flick_sensitivity_preference")?.apply {
            summary = when (this.value) {
                in 0..50 -> getString(R.string.sensitivity_very_high)
                in 51..90 -> getString(R.string.sensitivity_high)
                in 91..110 -> getString(R.string.sensitivity_normal)
                in 111..150 -> getString(R.string.sensitivity_less)
                in 151..200 -> getString(R.string.sensitivity_low)
                else -> ""
            }
            setOnPreferenceChangeListener { pref, newValue ->
                val sbp = pref as SeekBarPreference
                val raw = (newValue as Int)
                val inc = sbp.seekBarIncrement
                val rounded = (raw + inc / 2) / inc * inc
                summary = when (rounded) {
                    in 0..50 -> getString(R.string.sensitivity_very_high)
                    in 51..90 -> getString(R.string.sensitivity_high)
                    in 91..110 -> getString(R.string.sensitivity_normal)
                    in 111..150 -> getString(R.string.sensitivity_less)
                    in 151..200 -> getString(R.string.sensitivity_low)
                    else -> ""
                }
                return@setOnPreferenceChangeListener if (rounded != raw) {
                    sbp.value = rounded
                    false
                } else {
                    true
                }
            }
        }

        val keyboardUndoEnablePreference =
            findPreference<SwitchPreferenceCompat>("undo_enable_preference")
        keyboardUndoEnablePreference?.apply {
            appPreference.undo_enable_preference?.let {
                this.summary = if (it) {
                    resources.getString(R.string.undo_enable_summary_on)
                } else {
                    resources.getString(R.string.undo_enable_summary_off)
                }
            }
            this.setOnPreferenceChangeListener { _, newValue ->
                this.summary = if (newValue == true) {
                    resources.getString(R.string.undo_enable_summary_on)
                } else {
                    resources.getString(R.string.undo_enable_summary_off)
                }
                true
            }
        }

        val customRomajiPreference = findPreference<Preference>("custom_romaji_preference")
        customRomajiPreference?.setOnPreferenceClickListener {
            findNavController().navigate(
                R.id.action_navigation_setting_to_romajiMapFragment
            )
            true
        }

        val keyboardSettingPreference = findPreference<Preference>("keyboard_screen_preference")

        keyboardSettingPreference?.setOnPreferenceClickListener {
            findNavController().navigate(
                R.id.action_navigation_setting_to_keyboardSettingFragment
            )
            true
        }

        val openSourcePreference = findPreference<Preference>("preference_open_source")

        openSourcePreference?.setOnPreferenceClickListener {
            findNavController().navigate(
                R.id.action_navigation_dashboard_to_openSourceFragment
            )
            true
        }

        val mozcUTPersonName =
            findPreference<SwitchPreferenceCompat>("mozc_ut_person_name_preference")
        mozcUTPersonName?.apply {
            this.setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    kanaKanjiEngine.buildPersonNamesDictionary(requireContext())
                } else {
                    kanaKanjiEngine.releasePersonNamesDictionary()
                }
                true
            }
        }

        val mozcUTPlaces = findPreference<SwitchPreferenceCompat>("mozc_ut_places_preference")
        mozcUTPlaces?.apply {
            this.setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    kanaKanjiEngine.buildPlaceDictionary(requireContext())
                } else {
                    kanaKanjiEngine.releasePlacesDictionary()
                }
                true
            }
        }

        val mozcUTWiki = findPreference<SwitchPreferenceCompat>("mozc_ut_wiki_preference")
        mozcUTWiki?.apply {
            this.setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    kanaKanjiEngine.buildWikiDictionary(requireContext())
                } else {
                    kanaKanjiEngine.releaseWikiDictionary()
                }
                true
            }
        }

        val mozcUTNeologd = findPreference<SwitchPreferenceCompat>("mozc_ut_neologd_preference")
        mozcUTNeologd?.apply {
            this.setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    kanaKanjiEngine.buildNeologdDictionary(requireContext())
                } else {
                    kanaKanjiEngine.releaseNeologdDictionary()
                }
                true
            }
        }

        val mozcUTWeb = findPreference<SwitchPreferenceCompat>("mozc_ut_web_preference")
        mozcUTWeb?.apply {
            this.setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    kanaKanjiEngine.buildWebDictionary(requireContext())
                } else {
                    kanaKanjiEngine.releaseWebDictionary()
                }
                true
            }
        }

        val seedColorPickerPreference =
            findPreference<Preference>("keyboard_theme_fragment_preference")
        seedColorPickerPreference?.apply {
            isVisible = DynamicColors.isDynamicColorAvailable()
            setOnPreferenceClickListener {
                showColorPickerDialog()
                true
            }
        }

    }

    private fun isKeyboardBoardEnabled(): Boolean? {
        val imm = getSystemService(requireContext(), InputMethodManager::class.java)
        return imm?.enabledInputMethodList?.any { it.packageName == requireContext().packageName }
    }

    private fun goToKeyboardSettingScreen() {
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        requireActivity().startActivity(intent)
    }

    @SuppressLint("CheckResult")
    private fun showColorPickerDialog() {
        // Get the currently saved color to show as the initial selection
        val initialColor = appPreference.seedColor

        MaterialDialog(requireContext()).show {
            title(text = getString(R.string.keyboard_theme_dialog_title))
            // Show a grid of predefined colors
            colorChooser(
                colors = intArrayOf(
                    0x00000000,
                    ContextCompat.getColor(requireContext(), com.kazumaproject.core.R.color.violet),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.violet_light
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.violet_dark
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.mint
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.mint_light
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.mint_dark
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.sky
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.sky_light
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.sky_dark
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.orange2
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.orange_light
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.orange_dark
                    ),
                ),
                initialSelection = initialColor,
                allowCustomArgb = true // Allow user to pick any color
            ) { _, color ->
                // When a color is selected:
                // 1. Save the new color to your AppPreference
                appPreference.seedColor = color

                // 2. Recreate the Activity to apply the new theme immediately
                requireActivity().recreate()
            }
            positiveButton(android.R.string.ok)
            negativeButton(android.R.string.cancel)
        }
    }

}
