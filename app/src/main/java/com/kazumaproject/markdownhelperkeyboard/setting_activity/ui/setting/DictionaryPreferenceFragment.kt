package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaTranslationManager
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DictionaryPreferenceFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var appPreference: AppPreference

    @Inject
    lateinit var kanaKanjiEngine: KanaKanjiEngine

    @Inject
    lateinit var gemmaTranslationManager: GemmaTranslationManager

    private val openGemmaModelLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            importGemmaModel(uri)
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_dictionary, rootKey)

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

        val systemUserDictionaryBuilderPreference =
            findPreference<Preference>("system_user_dictionary_builder_preference")
        systemUserDictionaryBuilderPreference?.setOnPreferenceClickListener {
            findNavController().navigate(
                R.id.action_navigation_setting_to_systemUserDictionaryBuilderFragment
            )
            true
        }

        val ngramRulePreference = findPreference<Preference>("n_gram_rule_preference")
        ngramRulePreference?.setOnPreferenceClickListener {
            findNavController().navigate(
                R.id.action_navigation_setting_to_ngramRuleFragment
            )
            true
        }

        val learnDictionaryPrefixSeekBar =
            findPreference<SeekBarPreference>("learn_prediction_preference")
        learnDictionaryPrefixSeekBar?.apply {
            appPreference.learn_prediction_preference.let {
                this.summary =
                    resources.getString(R.string.learn_dictionary_prefix_match_summary, it)
            }
            this.setOnPreferenceChangeListener { _, newValue ->
                this.summary =
                    resources.getString(
                        R.string.learn_dictionary_prefix_match_summary,
                        newValue as Int
                    )
                true
            }
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

        val mozcUTPersonName =
            findPreference<SwitchPreferenceCompat>("mozc_ut_person_name_preference")
        mozcUTPersonName?.apply {
            this.setOnPreferenceChangeListener { _, newValue ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    if (newValue as Boolean) {
                        kanaKanjiEngine.buildPersonNamesDictionary(requireContext())
                    } else {
                        kanaKanjiEngine.releasePersonNamesDictionary()
                    }
                }
                true
            }
        }

        val mozcUTPlaces = findPreference<SwitchPreferenceCompat>("mozc_ut_places_preference")
        mozcUTPlaces?.apply {
            this.setOnPreferenceChangeListener { _, newValue ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    if (newValue as Boolean) {
                        kanaKanjiEngine.buildPlaceDictionary(requireContext())
                    } else {
                        kanaKanjiEngine.releasePlacesDictionary()
                    }
                }
                true
            }
        }

        val mozcUTWiki = findPreference<SwitchPreferenceCompat>("mozc_ut_wiki_preference")
        mozcUTWiki?.apply {
            this.setOnPreferenceChangeListener { _, newValue ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    if (newValue as Boolean) {
                        kanaKanjiEngine.buildWikiDictionary(requireContext())
                    } else {
                        kanaKanjiEngine.releaseWikiDictionary()
                    }
                }
                true
            }
        }

        val mozcUTNeologd = findPreference<SwitchPreferenceCompat>("mozc_ut_neologd_preference")
        mozcUTNeologd?.apply {
            this.setOnPreferenceChangeListener { _, newValue ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    if (newValue as Boolean) {
                        kanaKanjiEngine.buildNeologdDictionary(requireContext())
                    } else {
                        kanaKanjiEngine.releaseNeologdDictionary()
                    }
                }
                true
            }
        }

        val mozcUTWeb = findPreference<SwitchPreferenceCompat>("mozc_ut_web_preference")
        mozcUTWeb?.apply {
            this.setOnPreferenceChangeListener { _, newValue ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    if (newValue as Boolean) {
                        kanaKanjiEngine.buildWebDictionary(requireContext())
                    } else {
                        kanaKanjiEngine.releaseWebDictionary()
                    }
                }
                true
            }
        }

        val gemmaTranslationSwitch =
            findPreference<SwitchPreferenceCompat>("gemma_translation_enable_preference")
        gemmaTranslationSwitch?.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as Boolean
            appPreference.enable_gemma_translation_preference = enabled
            updateGemmaModelSummary(getString(R.string.gemma_translation_model_summary_loading))
            viewLifecycleOwner.lifecycleScope.launch {
                if (enabled) {
                    gemmaTranslationManager.initializeIfEnabled(forceReload = false)
                } else {
                    gemmaTranslationManager.disable()
                }
                updateGemmaModelSummary()
            }
            true
        }

        findPreference<ListPreference>("gemma_translation_backend_preference")?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            setOnPreferenceChangeListener { _, newValue ->
                appPreference.gemma_translation_backend_preference = newValue as String
                if (appPreference.enable_gemma_translation_preference) {
                    updateGemmaModelSummary(getString(R.string.gemma_translation_model_summary_loading))
                    viewLifecycleOwner.lifecycleScope.launch {
                        gemmaTranslationManager.initializeIfEnabled(forceReload = true)
                        updateGemmaModelSummary()
                    }
                }
                true
            }
        }

        findPreference<ListPreference>("gemma_translation_target_language_preference")?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            setOnPreferenceChangeListener { _, newValue ->
                appPreference.gemma_translation_target_language_preference = newValue as String
                true
            }
        }

        val gemmaModelPreference =
            findPreference<Preference>("gemma_translation_model_preference")
        gemmaModelPreference?.setOnPreferenceClickListener {
            openGemmaModelLauncher.launch(arrayOf("*/*"))
            true
        }

        findPreference<Preference>("gemma_prompt_template_management_preference")?.setOnPreferenceClickListener {
            findNavController().navigate(
                R.id.action_navigation_setting_to_gemmaPromptTemplateFragment
            )
            true
        }

        updateGemmaModelSummary()
    }

    private fun importGemmaModel(uri: Uri) {
        updateGemmaModelSummary(getString(R.string.gemma_translation_model_summary_loading))
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                gemmaTranslationManager.importModelFromUri(uri)
                gemmaTranslationManager.initializeIfEnabled(forceReload = true)
            }.onSuccess {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.gemma_translation_model_import_success),
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    error.localizedMessage
                        ?: getString(R.string.gemma_translation_model_import_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }

            updateGemmaModelSummary()
        }
    }

    private fun updateGemmaModelSummary(summaryOverride: String? = null) {
        findPreference<Preference>("gemma_translation_model_preference")?.summary =
            summaryOverride ?: gemmaTranslationManager.getModelSummary()
    }
}
