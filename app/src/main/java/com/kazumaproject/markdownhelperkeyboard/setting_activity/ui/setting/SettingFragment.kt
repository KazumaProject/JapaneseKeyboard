package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat.getSystemService
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
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

        val sumireInputPreference =
            findPreference<PreferenceCategory>("sumire_input_preference_category")

        val appVersionPreference = findPreference<Preference>("app_version_preference")
        appVersionPreference?.apply {
            summary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                "version name: ${packageInfo.versionName}\nversion code: ${packageInfo.longVersionCode}"
            } else {
                "version name: ${packageInfo.versionName}\nversion code: ${packageInfo.versionCode}"
            }
            setOnPreferenceClickListener {
                count += 1
                if (count >= 5) {
                    sumireInputPreference?.isVisible = true
                }
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
                "EMOJI" -> "絵文字"
                "EMOTICON" -> "顔文字"
                "SYMBOL" -> "記号"
                "CLIPBOARD" -> "クリップボードの履歴"
                else -> "記号キーボードの初期画面を選択します"
            }
            setOnPreferenceChangeListener { _, newValue ->
                summary = when (newValue) {
                    "EMOJI" -> "絵文字"
                    "EMOTICON" -> "顔文字"
                    "SYMBOL" -> "記号"
                    "CLIPBOARD" -> "クリップボードの履歴"
                    else -> "記号キーボードの初期画面を選択します"
                }
                true
            }
        }

        val keyboardUndoEnablePreference =
            findPreference<SwitchPreferenceCompat>("undo_enable_preference")
        keyboardUndoEnablePreference?.apply {
            appPreference.undo_enable_preference?.let {
                this.summary = if (it) {
                    "確定した文字を削除したときに、元に戻せる機能を有効にします"
                } else {
                    "確定した文字を削除したときに、元に戻せる機能を無効にします"
                }
            }
            this.setOnPreferenceChangeListener { _, newValue ->
                this.summary = if (newValue == true) {
                    "確定した文字を削除したときに、元に戻せる機能を有効にします"
                } else {
                    "確定した文字を削除したときに、元に戻せる機能を無効にします"
                }
                true
            }
        }

        val spaceHankakuPreference = findPreference<SwitchPreferenceCompat>("space_key_preference")
        spaceHankakuPreference?.apply {
            appPreference.space_hankaku_preference?.let {
                this.title = if (it) {
                    "空白を半角入力"
                } else {
                    "空白を全角入力"
                }
                this.summary = if (it) {
                    "現在、半角入力です"
                } else {
                    "現在、全角入力です"
                }
            }
            this.setOnPreferenceChangeListener { _, newValue ->
                this.title = if (newValue == true) {
                    "空白を半角入力"
                } else {
                    "空白を全角入力"
                }
                this.summary = if (newValue == true) {
                    "現在、半角入力です"
                } else {
                    "現在、全角入力です"
                }
                true
            }
        }

        val sumireKeyboardInputModePreference =
            findPreference<ListPreference>("sumire_keyboard_input_type_preference")

        sumireKeyboardInputModePreference?.apply {
            when (value) {
                "flick-default" -> {
                    summary = "フリック入力 - Default"
                }

                "flick-circle" -> {
                    summary = "フリック入力 - Circle"
                }

                "flick-sumire" -> {
                    summary = "スミレ入力"
                }
            }
            setOnPreferenceChangeListener { preference, newValue ->
                if (newValue is String) {
                    when (newValue) {
                        "flick-default" -> {
                            preference.summary = "フリック入力 - Default"
                        }

                        "flick-circle" -> {
                            preference.summary = "フリック入力 - Circle"
                        }

                        "flick-sumire" -> {
                            preference.summary = "スミレ入力"
                        }
                    }
                }
                true
            }
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

}
