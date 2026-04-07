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
import androidx.preference.SwitchPreferenceCompat
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaTranslationManager
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GemmaPreferenceFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var appPreference: AppPreference

    @Inject
    lateinit var gemmaTranslationManager: GemmaTranslationManager

    private val openGemmaModelLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            importGemmaModel(uri)
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_gemma, rootKey)

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

        findPreference<Preference>("gemma_translation_model_preference")?.setOnPreferenceClickListener {
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
            }.onSuccess { initialized ->
                Toast.makeText(
                    requireContext(),
                    if (initialized) {
                        getString(R.string.gemma_translation_model_import_success)
                    } else {
                        gemmaTranslationManager.getModelSummary()
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { throwable ->
                Toast.makeText(
                    requireContext(),
                    throwable.localizedMessage
                        ?: getString(R.string.gemma_translation_model_import_failed),
                    Toast.LENGTH_LONG
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
