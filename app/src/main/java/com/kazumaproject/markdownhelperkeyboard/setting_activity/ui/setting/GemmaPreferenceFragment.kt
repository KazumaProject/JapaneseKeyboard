package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.color.colorChooser
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaLoadState
import com.kazumaproject.markdownhelperkeyboard.gemma.AiModelFormat
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaTranslationManager
import com.kazumaproject.markdownhelperkeyboard.gemma.handwriting.GemmaHandwritingSettings
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class GemmaPreferenceFragment : AsyncPreferenceFragment() {
    override val preferencesXmlRes: Int = R.xml.pref_gemma

    @Inject
    lateinit var appPreference: AppPreference

    @Inject
    lateinit var gemmaTranslationManager: GemmaTranslationManager

    private val openGemmaModelLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            importGemmaModel(uri)
        }

    private val openProjectorLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importGemmaModel(uri, projector = true)
    }
    private var projectorFiles: List<File> = emptyList()
    private var selectedProjectorPath = ""
    private var selectedFormat: AiModelFormat? = null

    private var installedModels: List<com.kazumaproject.markdownhelperkeyboard.gemma.InstalledGemmaModel> = emptyList()
    private var selectedModelPath: String? = null
    private var modelSummary: String? = null
    private var importingModel = false

    override suspend fun preparePreferenceData(context: android.content.Context) {
        val snapshot = settingsIo(SettingsLoadStage.MODELS) {
            Triple(
                gemmaTranslationManager.installedModels(),
                gemmaTranslationManager.selectedModel()?.file?.absolutePath,
                gemmaTranslationManager.getModelSummary(),
            )
        }
        installedModels = snapshot.first
        selectedModelPath = snapshot.second
        modelSummary = snapshot.third
        val projectors = settingsIo(SettingsLoadStage.MODELS) {
            Triple(gemmaTranslationManager.installedProjectors(), gemmaTranslationManager.projectorPath(),
                gemmaTranslationManager.selectedModel()?.file?.name)
        }
        projectorFiles = projectors.first
        selectedProjectorPath = projectors.second
        selectedFormat = projectors.third?.let(AiModelFormat::fromFilename)
    }

    override fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) {

        val floatingSettings = com.kazumaproject.markdownhelperkeyboard.gemma.floating.GemmaFloatingSettings(
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext()),
        )
        floatingSettings.ensureDefaults()
        fun refreshFloatingRules() {
            findPreference<ListPreference>(com.kazumaproject.markdownhelperkeyboard.gemma.floating.GemmaFloatingSettings.SELECTED)?.apply {
                entries = (listOf(getString(R.string.gemma_floating_none)) + floatingSettings.rules.map { it.name }).toTypedArray()
                entryValues = (listOf("") + floatingSettings.rules.map { it.id }).toTypedArray()
                value = floatingSettings.selectedId
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            }
        }
        refreshFloatingRules()
        findPreference<Preference>(com.kazumaproject.markdownhelperkeyboard.gemma.floating.GemmaFloatingSettings.MANAGE)?.setOnPreferenceClickListener {
            com.kazumaproject.markdownhelperkeyboard.gemma.floating.GemmaFloatingRuleEditor(
                requireContext(), floatingSettings, ::refreshFloatingRules,
            ).show()
            true
        }

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

        findPreference<SeekBarPreference>(
            AppPreference.GEMMA_HANDWRITING_AUTO_RECOGNITION_DELAY_KEY
        )?.apply {
            value = appPreference.gemma_handwriting_auto_recognition_delay_preference
            summary = getString(
                R.string.gemma_handwriting_auto_recognition_delay_value,
                value,
            )
            setOnPreferenceChangeListener { _, newValue ->
                val delayMs = (newValue as Int).coerceIn(
                    GemmaHandwritingSettings.MIN_AUTO_RECOGNITION_DELAY_MS,
                    GemmaHandwritingSettings.MAX_AUTO_RECOGNITION_DELAY_MS,
                )
                appPreference.gemma_handwriting_auto_recognition_delay_preference = delayMs
                summary = getString(
                    R.string.gemma_handwriting_auto_recognition_delay_value,
                    delayMs,
                )
                true
            }
        }

        findPreference<SeekBarPreference>(
            AppPreference.GEMMA_HANDWRITING_PEN_SIZE_KEY
        )?.apply {
            value = appPreference.gemma_handwriting_pen_size_preference
            summary = getString(R.string.gemma_handwriting_pen_size_value, value)
            setOnPreferenceChangeListener { _, newValue ->
                val penSizeDp = (newValue as Int).coerceIn(
                    GemmaHandwritingSettings.MIN_PEN_SIZE_DP,
                    GemmaHandwritingSettings.MAX_PEN_SIZE_DP,
                )
                appPreference.gemma_handwriting_pen_size_preference = penSizeDp
                summary = getString(R.string.gemma_handwriting_pen_size_value, penSizeDp)
                true
            }
        }

        findPreference<Preference>(AppPreference.GEMMA_HANDWRITING_PEN_COLOR_KEY)?.apply {
            summary = handwritingPenColorSummary(
                appPreference.gemma_handwriting_pen_color_preference,
            )
            setOnPreferenceClickListener {
                showHandwritingPenColorPicker { color ->
                    appPreference.gemma_handwriting_pen_color_preference = color
                    summary = handwritingPenColorSummary(
                        appPreference.gemma_handwriting_pen_color_preference,
                    )
                }
                true
            }
        }

        findPreference<ListPreference>(
            AppPreference.GEMMA_HANDWRITING_RECOGNITION_LANGUAGE_KEY
        )?.apply {
            value = appPreference.gemma_handwriting_recognition_language_preference
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            setOnPreferenceChangeListener { _, newValue ->
                appPreference.gemma_handwriting_recognition_language_preference =
                    newValue as String
                true
            }
        }

        val additionalInstructionPreference = findPreference<EditTextPreference>(
            AppPreference.GEMMA_HANDWRITING_ADDITIONAL_INSTRUCTION_KEY
        )
        additionalInstructionPreference?.apply {
            text = appPreference.gemma_handwriting_additional_instruction_preference
            updateAdditionalInstructionSummary(this)
            setOnBindEditTextListener { editText ->
                editText.inputType =
                    InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                editText.isSingleLine = false
                editText.minLines = 8
                editText.maxLines = 14
                editText.gravity = Gravity.TOP or Gravity.START
                editText.setHorizontallyScrolling(false)
                editText.setSelection(editText.text?.length ?: 0)
            }
            setOnPreferenceChangeListener { _, newValue ->
                val instruction = (newValue as String).trim()
                if (
                    instruction.length >
                    GemmaHandwritingSettings.MAX_ADDITIONAL_INSTRUCTION_LENGTH
                ) {
                    Toast.makeText(
                        requireContext(),
                        R.string.gemma_handwriting_additional_instruction_too_long_error,
                        Toast.LENGTH_SHORT,
                    ).show()
                    false
                } else {
                    appPreference.gemma_handwriting_additional_instruction_preference =
                        instruction
                    text = instruction
                    updateAdditionalInstructionSummary(this)
                    false
                }
            }
        }

        findPreference<Preference>(
            AppPreference.GEMMA_HANDWRITING_RESET_PROMPT_KEY
        )?.setOnPreferenceClickListener {
            additionalInstructionPreference?.text = ""
            appPreference.resetGemmaHandwritingPromptToDefault()
            additionalInstructionPreference?.let(::updateAdditionalInstructionSummary)
            Toast.makeText(
                requireContext(),
                R.string.gemma_handwriting_prompt_reset_done,
                Toast.LENGTH_SHORT,
            ).show()
            true
        }

        findPreference<ListPreference>("gemma_model_selection_preference")?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                if (gemmaTranslationManager.loadState.value is GemmaLoadState.Loading) {
                    return@setOnPreferenceChangeListener false
                }
                val selected = newValue as String
                viewLifecycleOwner.lifecycleScope.launch {
                    val accepted = settingsIo(SettingsLoadStage.MODELS) {
                        gemmaTranslationManager.selectModel(selected)
                    }
                    if (accepted) {
                        refreshPreferenceData {
                            preparePreferenceData(requireContext())
                            refreshInstalledModels()
                            updateGemmaModelSummary()
                        }
                        if (appPreference.enable_gemma_translation_preference) {
                            setGemmaLoadControlsEnabled(false)
                            gemmaTranslationManager.initializeIfEnabled(forceReload = true)
                        }
                    }
                }
                // The worker validates the file before publishing and persisting selection.
                false
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

        findPreference<Preference>("gemma_supported_models_preference")?.setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.gemma_supported_models_title)
                .setMessage(R.string.ai_supported_models_details)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            true
        }

        findPreference<Preference>("ai_projector_preference")?.setOnPreferenceClickListener {
            showProjectors()
            true
        }
        refreshInstalledModels()
        updateGemmaModelSummary()
        applyLegacySearchResultFilterIfNeeded()
        observeGemmaLoadState()
    }

    private fun showProjectors() {
        val labels = listOf(getString(R.string.ai_projector_none)) + projectorFiles.map { it.name } + getString(R.string.ai_projector_import)
        val paths = listOf("") + projectorFiles.map { it.absolutePath }
        AlertDialog.Builder(requireContext()).setTitle(R.string.ai_projector_title)
            .setSingleChoiceItems(labels.toTypedArray(), paths.indexOf(selectedProjectorPath)) { dialog, index ->
                dialog.dismiss()
                if (index == labels.lastIndex) {
                    openProjectorLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                } else {
                    setGemmaLoadControlsEnabled(false)
                    viewLifecycleOwner.lifecycleScope.launch {
                        runCatching {
                            gemmaTranslationManager.selectProjector(paths[index])
                            gemmaTranslationManager.initializeIfEnabled(forceReload = true)
                        }.onFailure {
                            Toast.makeText(requireContext(), it.localizedMessage, Toast.LENGTH_LONG).show()
                        }
                        applyGemmaLoadState(gemmaTranslationManager.loadState.value)
                    }
                }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun updateAdditionalInstructionSummary(preference: EditTextPreference) {
        preference.summary = getString(
            if (appPreference.gemma_handwriting_additional_instruction_preference.isBlank()) {
                R.string.gemma_handwriting_additional_instruction_summary_default
            } else {
                R.string.gemma_handwriting_additional_instruction_summary_custom
            }
        )
    }

    private fun handwritingPenColorSummary(color: Int): String {
        return if (color == GemmaHandwritingSettings.AUTOMATIC_PEN_COLOR) {
            getString(R.string.gemma_handwriting_pen_color_auto_value)
        } else {
            getString(R.string.gemma_handwriting_pen_color_value, color)
        }
    }

    @SuppressLint("CheckResult")
    private fun showHandwritingPenColorPicker(onColorSelected: (Int) -> Unit) {
        MaterialDialog(requireContext()).show {
            title(text = getString(R.string.gemma_handwriting_pen_color_title))
            colorChooser(
                colors = intArrayOf(
                    GemmaHandwritingSettings.AUTOMATIC_PEN_COLOR,
                    Color.BLACK,
                    Color.WHITE,
                    Color.DKGRAY,
                    Color.LTGRAY,
                    Color.rgb(30, 136, 229),
                    Color.rgb(0, 137, 123),
                    Color.rgb(67, 160, 71),
                    Color.rgb(251, 140, 0),
                    Color.rgb(229, 57, 53),
                    Color.rgb(142, 36, 170),
                ),
                initialSelection = appPreference.gemma_handwriting_pen_color_preference,
                allowCustomArgb = true,
            ) { _, color ->
                onColorSelected(GemmaHandwritingSettings.normalizePenColor(color))
            }
            positiveButton(android.R.string.ok)
            negativeButton(android.R.string.cancel)
        }
    }

    private fun importGemmaModel(uri: Uri, projector: Boolean = false) {
        importingModel = true
        renderGemmaLoadState(gemmaTranslationManager.loadState.value)
        updateGemmaModelSummary(loadingSummary(appPreference.gemma_translation_backend_preference))
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                if (projector) {
                    val path = gemmaTranslationManager.importProjectorFromUri(uri)
                    gemmaTranslationManager.selectProjector(path)
                } else gemmaTranslationManager.importModelFromUri(uri)
                preparePreferenceData(requireContext())
                refreshInstalledModels()
                gemmaTranslationManager.initializeIfEnabled(forceReload = true)
            }.onSuccess { initialized ->
                Toast.makeText(
                    requireContext(),
                    if (initialized) {
                        getString(R.string.gemma_translation_model_import_success)
                    } else {
                        modelSummary ?: getString(R.string.gemma_translation_model_summary_missing)
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
            importingModel = false
            applyGemmaLoadState(gemmaTranslationManager.loadState.value)
        }
    }

    private fun updateGemmaModelSummary(summaryOverride: String? = null) {
        val summary = summaryOverride ?: modelSummary
        findPreference<Preference>("gemma_translation_model_preference")?.summary = summary
        findPreference<ListPreference>("gemma_model_selection_preference")?.summary = summary
    }

    private fun refreshInstalledModels() {
        val models = installedModels
        findPreference<Preference>("ai_projector_preference")?.apply {
            isEnabled = selectedFormat == AiModelFormat.GGUF && gemmaTranslationManager.loadState.value !is GemmaLoadState.Loading
            summary = selectedProjectorPath.takeIf { it.isNotBlank() }?.let { File(it).name }
                ?: getString(R.string.ai_projector_none)
        }
        findPreference<ListPreference>("gemma_model_selection_preference")?.apply {
            entries = models.map { it.selectionLabel }.toTypedArray()
            entryValues = models.map { it.file.absolutePath }.toTypedArray()
            value = selectedModelPath
            isEnabled = models.isNotEmpty() &&
                gemmaTranslationManager.loadState.value !is GemmaLoadState.Loading
        }
    }

    private fun observeGemmaLoadState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                gemmaTranslationManager.loadState.collectLatest { state ->
                    // Publish progress immediately; model scans must not delay or cover it.
                    renderGemmaLoadState(state)
                    if (state !is GemmaLoadState.Loading) {
                        refreshPreferenceData {
                            preparePreferenceData(requireContext())
                            refreshInstalledModels()
                            renderGemmaLoadState(gemmaTranslationManager.loadState.value)
                        }
                    }
                }
            }
        }
    }

    private fun applyGemmaLoadState(state: GemmaLoadState) {
        renderGemmaLoadState(state)
        if (state is GemmaLoadState.Loading) return
        launchPreferenceRefresh {
            preparePreferenceData(requireContext())
            refreshInstalledModels()
            renderGemmaLoadState(gemmaTranslationManager.loadState.value)
        }
    }

    private fun renderGemmaLoadState(state: GemmaLoadState) {
        val loading = state is GemmaLoadState.Loading
        findPreference<Preference>("ai_model_loading_preference")?.apply {
            val wasVisible = isVisible
            isVisible = loading || importingModel
            setTitle(if (loading) R.string.ai_model_loading_title else R.string.ai_model_importing_title)
            summary = if (state is GemmaLoadState.Loading) loadingSummary(state.backendPreference) else null
            if (isVisible && !wasVisible) scrollToPreference(this)
        }
        setGemmaLoadControlsEnabled(!loading && !importingModel)
        updateGemmaModelSummary(summaryForState(state))
    }

    private fun setGemmaLoadControlsEnabled(enabled: Boolean) {
        findPreference<SwitchPreferenceCompat>("gemma_translation_enable_preference")?.isEnabled = enabled
        findPreference<ListPreference>("gemma_translation_backend_preference")?.isEnabled = enabled
        findPreference<Preference>("ai_projector_preference")?.isEnabled = enabled && selectedFormat == AiModelFormat.GGUF
        findPreference<ListPreference>("gemma_model_selection_preference")?.isEnabled =
            enabled && installedModels.isNotEmpty()
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
                when (state.backend) {
                    getString(R.string.gemma_translation_backend_runtime_cpu_fallback) ->
                        "$ready\n${getString(R.string.gemma_translation_model_summary_cpu_fallback_ready)}"
                    getString(R.string.ai_npu_cpu_fallback) ->
                        "$ready\n${getString(R.string.ai_npu_cpu_fallback_summary)}"
                    else -> ready
                }
            }
            is GemmaLoadState.Failed ->
                getString(R.string.gemma_translation_model_summary_error, state.message)
        }
    }

    private fun loadingSummary(backendPreference: String): String {
        val backendLabel = when (backendPreference) {
            "gpu_if_available" -> getString(R.string.gemma_translation_backend_runtime_gpu)
            "npu_if_available" -> getString(R.string.ai_backend_npu)
            else -> getString(R.string.gemma_translation_backend_runtime_cpu)
        }
        return getString(R.string.gemma_translation_model_summary_loading_backend, backendLabel)
    }
}
