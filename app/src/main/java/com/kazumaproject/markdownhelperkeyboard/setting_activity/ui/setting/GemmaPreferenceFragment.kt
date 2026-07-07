package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaLoadState
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaTranslationManager
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
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
            if (gemmaTranslationManager.loadState.value is GemmaLoadState.Loading) {
                return@setOnPreferenceChangeListener false
            }
            val enabled = newValue as Boolean
            appPreference.enable_gemma_translation_preference = enabled
            setGemmaLoadControlsEnabled(false)
            updateGemmaModelSummary(
                if (enabled) {
                    loadingSummary(appPreference.gemma_translation_backend_preference)
                } else {
                    null
                }
            )
            viewLifecycleOwner.lifecycleScope.launch {
                if (enabled) {
                    gemmaTranslationManager.initializeIfEnabled(forceReload = false)
                } else {
                    gemmaTranslationManager.disable()
                }
            }
            true
        }

        findPreference<ListPreference>("gemma_translation_backend_preference")?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            setOnPreferenceChangeListener { _, newValue ->
                if (gemmaTranslationManager.loadState.value is GemmaLoadState.Loading) {
                    return@setOnPreferenceChangeListener false
                }
                appPreference.gemma_translation_backend_preference = newValue as String
                if (appPreference.enable_gemma_translation_preference) {
                    setGemmaLoadControlsEnabled(false)
                    updateGemmaModelSummary(loadingSummary(newValue))
                    viewLifecycleOwner.lifecycleScope.launch {
                        gemmaTranslationManager.initializeIfEnabled(forceReload = true)
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
            if (gemmaTranslationManager.loadState.value is GemmaLoadState.Loading) {
                return@setOnPreferenceClickListener true
            }
            openGemmaModelLauncher.launch(arrayOf("application/octet-stream", "*/*"))
            true
        }

        findPreference<Preference>("gemma_prompt_template_management_preference")?.setOnPreferenceClickListener {
            navigateSafely(
                R.id.gemmaPromptTemplateFragment
            )
            true
        }

        updateGemmaModelSummary()
        applyLegacySearchResultFilterIfNeeded()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scrollToHighlightedPreferenceAfterLayout(view)
        observeGemmaLoadState()
    }

    private fun importGemmaModel(uri: Uri) {
        setGemmaLoadControlsEnabled(false)
        updateGemmaModelSummary(loadingSummary(appPreference.gemma_translation_backend_preference))
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
            if (gemmaTranslationManager.loadState.value !is GemmaLoadState.Loading) {
                applyGemmaLoadState(gemmaTranslationManager.loadState.value)
            }
        }
    }

    private fun updateGemmaModelSummary(summaryOverride: String? = null) {
        findPreference<Preference>("gemma_translation_model_preference")?.summary =
            summaryOverride ?: gemmaTranslationManager.getModelSummary()
    }

    private fun observeGemmaLoadState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                gemmaTranslationManager.loadState.collect { state ->
                    applyGemmaLoadState(state)
                }
            }
        }
    }

    private fun applyGemmaLoadState(state: GemmaLoadState) {
        val loading = state is GemmaLoadState.Loading
        setGemmaLoadControlsEnabled(!loading)
        updateGemmaModelSummary(summaryForState(state))
    }

    private fun setGemmaLoadControlsEnabled(enabled: Boolean) {
        findPreference<SwitchPreferenceCompat>("gemma_translation_enable_preference")?.isEnabled = enabled
        findPreference<ListPreference>("gemma_translation_backend_preference")?.isEnabled = enabled
        findPreference<Preference>("gemma_translation_model_preference")?.isEnabled = enabled
    }

    private fun summaryForState(state: GemmaLoadState): String? {
        return when (state) {
            GemmaLoadState.Disabled -> null
            GemmaLoadState.MissingModel ->
                getString(R.string.gemma_translation_model_summary_missing)
            GemmaLoadState.UnsupportedAbi ->
                getString(R.string.gemma_translation_model_summary_unsupported_abi)
            is GemmaLoadState.Loading -> loadingSummary(state.backendPreference)
            is GemmaLoadState.Ready -> {
                val ready = getString(
                    R.string.gemma_translation_model_summary_ready,
                    File(state.modelPath).name,
                    state.backend,
                )
                if (state.backend == getString(R.string.gemma_translation_backend_runtime_cpu_fallback)) {
                    "$ready\n${getString(R.string.gemma_translation_model_summary_cpu_fallback_ready)}"
                } else {
                    ready
                }
            }
            is GemmaLoadState.Failed ->
                getString(R.string.gemma_translation_model_summary_error, state.message)
        }
    }

    private fun loadingSummary(backendPreference: String): String {
        val backendLabel = when (backendPreference) {
            "gpu_if_available" -> getString(R.string.gemma_translation_backend_runtime_gpu)
            else -> getString(R.string.gemma_translation_backend_runtime_cpu)
        }
        return getString(R.string.gemma_translation_model_summary_loading_backend, backendLabel)
    }
}
