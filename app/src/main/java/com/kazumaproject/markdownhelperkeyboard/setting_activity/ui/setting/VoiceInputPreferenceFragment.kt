package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.Preference
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.kazumaproject.markdownhelperkeyboard.ime_service.voice_input.AiVoiceInputManager
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaLoadState
import com.kazumaproject.markdownhelperkeyboard.gemma.AiModelFormat
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import java.io.File
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.voice_input.VoiceInputSpaceReplacement
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference

class VoiceInputPreferenceFragment : AsyncPreferenceFragment() {
    override val preferencesXmlRes: Int = R.xml.pref_voice_input

    @EntryPoint @InstallIn(SingletonComponent::class)
    interface Dependencies { fun voiceManager(): AiVoiceInputManager }
    private val manager by lazy { EntryPointAccessors.fromApplication(requireContext().applicationContext, Dependencies::class.java).voiceManager() }
    private var models: List<File> = emptyList()
    private var projectors: List<File> = emptyList()
    private var projector = ""
    private var state: GemmaLoadState = GemmaLoadState.Disabled
    private var importing = false
    private val importModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importFile(uri, false)
    }
    private val importProjector = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importFile(uri, true)
    }

    override suspend fun preparePreferenceData(context: android.content.Context) {
        if (!AppVariantConfig.hasGemma) return
        settingsIo(SettingsLoadStage.MODELS) {
            models = manager.models.installedModels().map { it.file }
            projectors = manager.models.installedProjectors()
            projector = manager.projectorPath()
        }
    }

    override fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) {
        findPreference<ListPreference>(AppPreference.VOICE_INPUT_ENGINE_KEY)?.apply {
            if (!AppVariantConfig.hasGemma) {
                entries = arrayOf(getString(R.string.voice_input_system)); entryValues = arrayOf("system"); value = "system"
            }
            setOnPreferenceChangeListener { _, value ->
                AppPreference.voice_input_engine = value as String
                if (AppVariantConfig.hasGemma) manager.cancel()
                renderModels()
                true
            }
        }
        findPreference<ListPreference>(AppPreference.VOICE_INPUT_MODEL_KEY)?.setOnPreferenceChangeListener { _, value ->
            manager.cancel()
            AppPreference.voice_input_model = value as String
            projector = manager.projectorPath()
            state = GemmaLoadState.Disabled
            renderModels()
            true
        }
        findPreference<ListPreference>(AppPreference.VOICE_INPUT_BACKEND_KEY)?.setOnPreferenceChangeListener { _, value ->
            manager.cancel()
            AppPreference.voice_input_backend = value as String
            state = GemmaLoadState.Disabled
            renderModels()
            true
        }
        findPreference<ListPreference>("voice_input_projector")?.setOnPreferenceChangeListener { _, value ->
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching { manager.models.selectProjectorForModel(AppPreference.voice_input_model, value as String) }
                    .onSuccess { projector = value as String; state = GemmaLoadState.Disabled; renderModels() }
                    .onFailure { showError(it) }
            }
            false
        }
        findPreference<Preference>("voice_input_import_model")?.setOnPreferenceClickListener { importModel.launch(arrayOf("*/*")); true }
        findPreference<Preference>("voice_input_import_projector")?.setOnPreferenceClickListener { importProjector.launch(arrayOf("*/*")); true }
        findPreference<Preference>("voice_input_load_model")?.setOnPreferenceClickListener {
            state = GemmaLoadState.Loading(AppPreference.voice_input_backend)
            renderModels()
            viewLifecycleOwner.lifecycleScope.launch { manager.validate() }
            true
        }
        renderModels()
        if (AppVariantConfig.hasGemma && view != null) viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                manager.loadState.collect { state = it; renderModels() }
            }
        }
        findPreference<SwitchPreferenceCompat>(AppPreference.VOICE_INPUT_SPACE_REPLACEMENT_ENABLED_KEY)
            ?.setOnPreferenceChangeListener { _, newValue ->
                updateCustomTextState(enabled = newValue as Boolean)
                true
            }
        findPreference<ListPreference>(AppPreference.VOICE_INPUT_SPACE_REPLACEMENT_KEY)
            ?.setOnPreferenceChangeListener { _, newValue ->
                updateCustomTextState(replacement = newValue as String)
                true
            }
        findPreference<EditTextPreference>(AppPreference.VOICE_INPUT_SPACE_CUSTOM_TEXT_KEY)?.apply {
            setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                editText.setSingleLine(true)
            }
            summaryProvider = androidx.preference.Preference.SummaryProvider<EditTextPreference> {
                if (it.text.isNullOrEmpty()) {
                    getString(R.string.voice_input_custom_text_empty_summary)
                } else {
                    getString(R.string.voice_input_custom_text_value_summary, it.text)
                }
            }
        }
        applyLegacySearchResultFilterIfNeeded()
        updateCustomTextState()
    }

    override fun onPreferencesResumed() {
        updateCustomTextState()
    }

    private fun renderModels() {
        val enabled = AppPreference.voice_input_engine == "ai"
        val busy = importing || state is GemmaLoadState.Loading
        findPreference<Preference>("voice_input_ai_options")?.apply { isVisible = AppVariantConfig.hasGemma; isEnabled = enabled }
        findPreference<Preference>("voice_input_loading")?.apply {
            isVisible = busy
            setTitle(if (importing) R.string.ai_model_importing_title else R.string.ai_model_loading_title)
        }
        for (key in listOf(AppPreference.VOICE_INPUT_ENGINE_KEY, AppPreference.VOICE_INPUT_MODEL_KEY,
            AppPreference.VOICE_INPUT_BACKEND_KEY, "voice_input_import_model", "voice_input_projector", "voice_input_import_projector", "voice_input_load_model")) {
            findPreference<Preference>(key)?.isEnabled = !busy && (key == AppPreference.VOICE_INPUT_ENGINE_KEY || enabled)
        }
        findPreference<ListPreference>(AppPreference.VOICE_INPUT_MODEL_KEY)?.apply {
            entries = models.map { it.name }.toTypedArray(); entryValues = models.map { it.absolutePath }.toTypedArray()
            value = AppPreference.voice_input_model
            summary = when (val current = state) {
                is GemmaLoadState.Ready -> "${File(current.modelPath).name} · ${current.backend}"
                is GemmaLoadState.Failed -> getString(R.string.gemma_translation_model_summary_error, current.message)
                is GemmaLoadState.Loading -> getString(R.string.gemma_translation_model_summary_loading)
                else -> value.takeIf { it.isNotBlank() }?.let { File(it).name } ?: getString(R.string.voice_input_model_missing)
            }
        }
        val gguf = AiModelFormat.fromFilename(AppPreference.voice_input_model) == AiModelFormat.GGUF
        findPreference<ListPreference>("voice_input_projector")?.apply {
            isVisible = gguf
            entries = (listOf(getString(R.string.gemma_floating_none)) + projectors.map { it.name }).toTypedArray()
            entryValues = (listOf("") + projectors.map { it.absolutePath }).toTypedArray()
            value = projector
            summary = File(projector).name.ifBlank { getString(R.string.gemma_floating_none) }
        }
        findPreference<Preference>("voice_input_import_projector")?.isVisible = gguf
    }

    private fun importFile(uri: android.net.Uri, isProjector: Boolean) {
        importing = true
        renderModels()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (isProjector) {
                    val path = manager.models.importProjectorFromUri(uri)
                    manager.models.selectProjectorForModel(AppPreference.voice_input_model, path)
                } else {
                    AppPreference.voice_input_model = manager.models.importModelFromUri(uri, selectForTranslation = false)
                }
                preparePreferenceData(requireContext())
                state = GemmaLoadState.Disabled
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { showError(error) }
            finally { importing = false; if (view != null) renderModels() }
        }
    }

    private fun showError(error: Throwable) {
        state = GemmaLoadState.Failed(error.message.orEmpty())
        renderModels()
    }

    private fun updateCustomTextState(
        enabled: Boolean = AppPreference.voice_input_space_replacement_enabled,
        replacement: String = AppPreference.voice_input_space_replacement,
    ) {
        findPreference<EditTextPreference>(AppPreference.VOICE_INPUT_SPACE_CUSTOM_TEXT_KEY)?.isEnabled =
            enabled && replacement == VoiceInputSpaceReplacement.CUSTOM.preferenceValue
    }
}
