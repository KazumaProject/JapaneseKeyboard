package com.kazumaproject.markdownhelperkeyboard.ime_service.voice_input

import android.content.Context
import androidx.preference.PreferenceManager
import com.kazumaproject.markdownhelperkeyboard.gemma.AiModelProfiles
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaLoadState
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaTranslationManager
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaRuntimeClient
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Voice selection is independent of the translation preference and uses the shared model store. */
@Singleton
class AiVoiceInputManager @Inject constructor(
    @ApplicationContext context: Context,
    private val runtime: GemmaRuntimeClient,
    val models: GemmaTranslationManager,
) {
    private val profiles = AiModelProfiles(PreferenceManager.getDefaultSharedPreferences(context))
    private val mutableState = MutableStateFlow<GemmaLoadState>(GemmaLoadState.Disabled)
    val loadState = mutableState.asStateFlow()

    fun projectorPath(): String = profiles.projectorPath(AppPreference.voice_input_model)

    private fun config(settings: VoiceInputSettings): GemmaRuntimeClient.ModelConfig {
        require(settings.modelPath.isNotBlank() && File(settings.modelPath).isFile) { "Select an installed voice model." }
        return GemmaRuntimeClient.ModelConfig(settings.modelPath, settings.backend, profiles.projectorPath(settings.modelPath))
    }

    suspend fun validate(settings: VoiceInputSettings = AppPreference.voice_input_settings): Boolean {
        mutableState.value = GemmaLoadState.Loading(settings.backend)
        return try {
            val backend = runtime.runVoiceModel(config(settings))
            mutableState.value = GemmaLoadState.Ready(backend, settings.modelPath, 2)
            true
        } catch (cancelled: CancellationException) {
            runtime.cancelVoice()
            mutableState.value = GemmaLoadState.Disabled
            throw cancelled
        } catch (error: Exception) {
            mutableState.value = GemmaLoadState.Failed(error.message.orEmpty())
            false
        }
    }

    suspend fun transcribe(audioPath: String, language: String, settings: VoiceInputSettings): String {
        require(File(audioPath).isFile) { "Recorded audio is missing." }
        val model = config(settings)
        val prompt = "Transcribe the speech in this audio in ${if (language.startsWith("ja")) "Japanese" else "English"}. " +
            "Return only the spoken words. Do not translate or add commentary."
        return runtime.runVoiceModel(model, prompt, audioPath).trim().also {
            check(it.isNotBlank()) { "No speech was recognized." }
            check(settings.modelPath == AppPreference.voice_input_model && settings.backend == AppPreference.voice_input_backend &&
                model.projectorPath == profiles.projectorPath(settings.modelPath) && AppPreference.voice_input_engine == "ai") {
                "Voice input settings changed."
            }
        }
    }

    fun cancel() {
        runtime.cancelVoice()
        mutableState.value = GemmaLoadState.Disabled
    }
}
