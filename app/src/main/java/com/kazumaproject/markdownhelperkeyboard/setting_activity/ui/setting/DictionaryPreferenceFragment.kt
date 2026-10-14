package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.ClassifiedDictionaries
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCompatibilityProblem
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCompatibilityResult
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCompatibilityValidator
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileSpecs
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DictionaryPreferenceFragment : AsyncPreferenceFragment() {
    override val preferencesXmlRes: Int = R.xml.pref_dictionary

    @Inject
    lateinit var appPreference: AppPreference

    @Inject
    lateinit var storeProvider: javax.inject.Provider<DictionaryOverrideStore>
    @Inject
    lateinit var validatorProvider: javax.inject.Provider<DictionaryCompatibilityValidator>
    private lateinit var store: DictionaryOverrideStore
    private var preparedRevision: Long = Long.MIN_VALUE
    private var categoryProblems: Map<DictionaryCategory, List<DictionaryCompatibilityProblem>> = emptyMap()

    override suspend fun preparePreferenceData(context: Context) {
        settingsIo(SettingsLoadStage.DATABASE) {
            store = storeProvider.get()
            val validator = validatorProvider.get()
            categoryProblems = ClassifiedDictionaries.all.associate { spec ->
                val files = DictionaryFileSpecs.forCategory(spec.category)
                val result = if (files.all { store.isValidOverride(it.key) }) {
                    validator.validateCategoryReplacement(spec.category)
                } else DictionaryCompatibilityResult.Compatible
                spec.category to ((result as? DictionaryCompatibilityResult.Incompatible)?.problems.orEmpty())
            }
            preparedRevision = store.currentRevision
        }
    }

    override fun onPreferencesResumed() {
        if (store.currentRevision != preparedRevision) {
            reloadPreferences()
            return
        }
        ClassifiedDictionaries.all.forEach { spec ->
            findPreference<SwitchPreferenceCompat>(spec.preferenceKey)?.isChecked = store.isOptionalBundledEnabled(spec.category)
        }
    }

    override fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) {

        ClassifiedDictionaries.all.forEach { spec ->
            findPreference<SwitchPreferenceCompat>(spec.preferenceKey)?.apply {
                isPersistent = false
                isChecked = store.isOptionalBundledEnabled(spec.category)
                setOnPreferenceChangeListener { _, value ->
                    val enabled = value == true
                    val problems = categoryProblems[spec.category].orEmpty()
                    store.setDisableableCategoryEnabled(spec.category, enabled, problems.isEmpty())
                    preparedRevision = store.currentRevision
                    isChecked = enabled
                    if (enabled && problems.isNotEmpty()) {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.external_dictionary_compat_dialog_title)
                            .setMessage((listOf(getString(R.string.external_dictionary_compat_imported_but_not_usable),
                                getString(R.string.external_dictionary_compat_import_matching_common_files)) +
                                problems.map { getString(it.messageResId, *it.messageArgs.toTypedArray()) }).joinToString("\n"))
                            .setPositiveButton(android.R.string.ok, null).show()
                    }
                    true
                }
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

        findPreference<Preference>("ng_word_preference")?.setOnPreferenceClickListener {
            navigateSafely(
                R.id.ngWordFragment
            )
            true
        }

        findPreference<Preference>("system_user_dictionary_builder_preference")?.setOnPreferenceClickListener {
            navigateSafely(
                R.id.systemUserDictionaryBuilderFragment
            )
            true
        }

        findPreference<Preference>("n_gram_rule_preference")?.setOnPreferenceClickListener {
            navigateSafely(
                R.id.ngramRuleFragment
            )
            true
        }

        findPreference<Preference>("candidate_order_override_preference")?.setOnPreferenceClickListener {
            navigateSafely(R.id.candidateOrderOverrideFragment)
            true
        }

        findPreference<Preference>("custom_zero_query_dictionary_preference")?.setOnPreferenceClickListener {
            navigateSafely(R.id.customZeroQueryDictionaryFragment)
            true
        }

        findPreference<Preference>("external_dictionary_settings_preference")?.setOnPreferenceClickListener {
            navigateSafely(R.id.externalDictionarySettingsFragment)
            true
        }

        val learnDictionaryPrefixSeekBar =
            findPreference<SeekBarPreference>("learn_prediction_preference")
        learnDictionaryPrefixSeekBar?.apply {
            appPreference.learn_prediction_preference.let {
                summary = resources.getString(R.string.learn_dictionary_prefix_match_summary, it)
            }
            setOnPreferenceChangeListener { _, newValue ->
                summary =
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
                summary = resources.getString(R.string.user_dictionary_prefix_match_summary, it)
            }
            setOnPreferenceChangeListener { _, newValue ->
                summary =
                    resources.getString(
                        R.string.user_dictionary_prefix_match_summary,
                        newValue as Int
                    )
                true
            }
        }

        applyLegacySearchResultFilterIfNeeded()
    }

}
