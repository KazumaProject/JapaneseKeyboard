package com.kazumaproject.markdownhelperkeyboard.gemma

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.preference.PreferenceManager
import org.json.JSONObject
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaMediaType
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaRuntimeClient
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

sealed interface GemmaLoadState {
    data object Disabled : GemmaLoadState
    data object MissingModel : GemmaLoadState
    data object UnsupportedAbi : GemmaLoadState
    data class Loading(val backendPreference: String) : GemmaLoadState
    data class Ready(val backend: String, val modelPath: String, val modalityFlags: Int = 0) : GemmaLoadState
    data class Failed(val message: String) : GemmaLoadState
}

/**
 * Main-process facade for Gemma. LiteRT-LM itself is owned by GemmaRuntimeService in `:gemma`.
 */
@Singleton
class GemmaTranslationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreference: AppPreference,
    private val runtimeClient: GemmaRuntimeClient,
) {
    internal enum class TranslationTargetLanguage(
        val preferenceValue: String,
        val promptLanguageName: String,
        val languageCode: String,
    ) {
        English("en", "English", "en"),
        Japanese("ja", "Japanese", "ja"),
        Korean("ko", "Korean", "ko"),
        ChineseSimplified("zh-Hans", "Simplified Chinese", "zh-Hans"),
        ChineseTraditional("zh-Hant", "Traditional Chinese", "zh-Hant"),
        Spanish("es", "Spanish", "es"),
        French("fr", "French", "fr"),
        German("de", "German", "de"),
        Italian("it", "Italian", "it"),
        Portuguese("pt", "Portuguese", "pt"),
        Russian("ru", "Russian", "ru"),
        Arabic("ar", "Arabic", "ar"),
        Hindi("hi", "Hindi", "hi"),
        Indonesian("id", "Indonesian", "id"),
        Thai("th", "Thai", "th"),
        Vietnamese("vi", "Vietnamese", "vi"),
        Turkish("tr", "Turkish", "tr"),
        Polish("pl", "Polish", "pl"),
        Dutch("nl", "Dutch", "nl"),
        Ukrainian("uk", "Ukrainian", "uk");

        companion object {
            fun fromPreference(value: String): TranslationTargetLanguage {
                return entries.firstOrNull { it.preferenceValue == value } ?: English
            }
        }
    }

    private val profiles = AiModelProfiles(PreferenceManager.getDefaultSharedPreferences(context))
    private val modelRevision = java.util.concurrent.atomic.AtomicLong()
    private val initializeMutex = Mutex()
    private val _loadState = MutableStateFlow<GemmaLoadState>(GemmaLoadState.Disabled)
    val loadState: StateFlow<GemmaLoadState> = _loadState.asStateFlow()

    @Volatile
    private var lastErrorMessage: String? = null

    fun isTranslationAvailable(): Boolean = _loadState.value is GemmaLoadState.Ready

    fun imageInputCapability(): GemmaImageCapability {
        return GemmaImageCapabilityResolver.resolve(
            hasGemma = AppVariantConfig.hasGemma,
            loadState = _loadState.value,
        )
    }

    fun selectedModel(): InstalledGemmaModel? {
        return resolveModelFile()?.let { InstalledGemmaModel(it, descriptorFor(it)) }
    }

    fun installedModels(): List<InstalledGemmaModel> {
        val files = buildList<File> {
            modelDirectories().forEach { directory ->
                directory.listFiles()
                    ?.filterTo(this) { it.isFile && AiModelFormat.fromFilename(it.name) != null }
            }
            val preferred = appPreference.gemma_translation_model_path_preference
                .takeIf { it.isNotBlank() }
                ?.let(::File)
            if (preferred?.isFile == true && none { it.absolutePath == preferred.absolutePath }) {
                add(preferred)
            }
        }
        return files.distinctBy { it.absolutePath }
            .map { InstalledGemmaModel(it, descriptorFor(it)) }
            .sortedWith(compareBy({ it.descriptor.displayName }, { it.file.name }))
    }

    fun selectModel(modelPath: String): Boolean {
        val selected = File(modelPath)
        if (!selected.isFile || AiModelFormat.fromFilename(selected.name) == null) return false
        modelRevision.incrementAndGet()
        runtimeClient.cancelActive()
        appPreference.gemma_translation_model_path_preference = selected.absolutePath
        lastErrorMessage = null
        _loadState.value = GemmaLoadState.Disabled
        return true
    }

    fun getModelSummary(): String {
        when (val state = _loadState.value) {
            GemmaLoadState.Disabled -> Unit
            GemmaLoadState.MissingModel ->
                return context.getString(R.string.gemma_translation_model_summary_missing)
            GemmaLoadState.UnsupportedAbi ->
                return context.getString(R.string.gemma_translation_model_summary_unsupported_abi)
            is GemmaLoadState.Loading ->
                return context.getString(R.string.gemma_translation_model_summary_loading)
            is GemmaLoadState.Ready -> {
                val file = File(state.modelPath)
                val descriptor = descriptorFor(file)
                return context.getString(
                    R.string.gemma_translation_model_summary_ready,
                    "${descriptor.displayName} · ${descriptor.modalityLabel()}",
                    state.backend,
                )
            }
            is GemmaLoadState.Failed ->
                return context.getString(R.string.gemma_translation_model_summary_error, state.message)
        }

        val model = selectedModel()
        if (model != null) {
            return context.getString(
                R.string.gemma_translation_model_summary_imported,
                model.selectionLabel,
            )
        }
        return lastErrorMessage?.let {
            context.getString(R.string.gemma_translation_model_summary_error, it)
        } ?: context.getString(R.string.gemma_translation_model_summary_missing)
    }

    suspend fun importModelFromUri(uri: Uri, selectForTranslation: Boolean = true): String =
        importFile(uri, projector = false, selectForTranslation = selectForTranslation)

    suspend fun importProjectorFromUri(uri: Uri): String = importFile(uri, projector = true)

    fun projectorPath(): String = resolveModelFile()?.let { profiles.projectorPath(it.absolutePath) }.orEmpty()

    fun installedProjectors(): List<File> = projectorDirectory().listFiles().orEmpty()
        .filter { it.isFile && it.extension.equals("gguf", true) }.sortedBy { it.name.lowercase() }

    suspend fun selectProjectorForModel(modelPath: String, path: String) {
        if (appPreference.gemma_translation_model_path_preference == modelPath) {
            selectProjector(path)
        } else {
            require(AiModelFormat.fromFilename(modelPath) == AiModelFormat.GGUF) { "Select a GGUF model first." }
            if (path.isNotBlank()) {
                require(File(path).isFile) { "Projector file is missing." }
                require(JSONObject(runtimeClient.inspect(path, "gguf")).optBoolean("projector")) { "Select a multimodal projector." }
            }
            profiles.setProjectorPath(modelPath, path)
        }
    }

    suspend fun selectProjector(path: String) = initializeMutex.withLock {
        val model = requireNotNull(resolveModelFile()) { "Select a GGUF model first." }
        require(AiModelFormat.fromFilename(model.name) == AiModelFormat.GGUF) { "Projectors are only used with GGUF models." }
        if (path.isNotBlank()) {
            require(File(path).isFile) { "Projector file is missing." }
            require(JSONObject(runtimeClient.inspect(path, "gguf")).optBoolean("projector")) { "Select a multimodal projector." }
        }
        runtimeClient.cancelActive()
        runtimeClient.close()
        modelRevision.incrementAndGet()
        profiles.setProjectorPath(model.absolutePath, path)
        _loadState.value = GemmaLoadState.Disabled
    }

    suspend fun inspectModel(file: File): GemmaModelDescriptor {
        val format = requireNotNull(AiModelFormat.fromFilename(file.name))
        val info = JSONObject(runtimeClient.inspect(file.absolutePath, format.extension))
        val flags = if (format == AiModelFormat.GGUF) {
            val projector = profiles.projectorPath(file.absolutePath)
            if (projector.isBlank()) 0 else JSONObject(runtimeClient.inspect(projector, "gguf")).optInt("flags")
        } else info.optInt("flags")
        return GemmaModelCatalog.descriptorFor(file).withCapabilities(flags)
    }

    fun supportsMedia(type: GemmaMediaType): Boolean {
        val ready = _loadState.value as? GemmaLoadState.Ready ?: return false
        return when (type) {
            GemmaMediaType.TEXT -> true
            GemmaMediaType.IMAGE -> ready.modalityFlags and 1 != 0
            GemmaMediaType.AUDIO -> ready.modalityFlags and 2 != 0
        }
    }

    private fun descriptorFor(file: File): GemmaModelDescriptor {
        val ready = _loadState.value as? GemmaLoadState.Ready
        val descriptor = GemmaModelCatalog.descriptorFor(file)
        return descriptor.copy(displayName = profiles.modelName(file.absolutePath).ifBlank { descriptor.displayName })
            .withCapabilities(if (ready?.modelPath == file.absolutePath) ready.modalityFlags else 0)
    }

    private fun projectorDirectory(): File = File(modelDirectory(), "projectors").apply { mkdirs() }

    private suspend fun importFile(uri: Uri, projector: Boolean, selectForTranslation: Boolean = true): String = withContext(Dispatchers.IO) {
      initializeMutex.withLock {
        val displayName = resolveDisplayName(uri)
        validateImportedModelDisplayName(displayName)
        val format = requireNotNull(AiModelFormat.fromFilename(displayName))
        if (projector) require(format == AiModelFormat.GGUF) { "Select a .gguf projector." }
        val destination = File(if (projector) projectorDirectory() else modelDirectory(), sanitizeModelFilename(displayName))
        val tempFile = File(destination.parentFile, "${destination.name}.${System.currentTimeMillis()}.tmp")
        val backupFile = File(destination.parentFile, "${destination.name}.bak")

        runCatching {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open AI model." }
                tempFile.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            validateModelFile(tempFile, format)
            runtimeClient.cancelActive()
            val info = JSONObject(runtimeClient.inspect(tempFile.absolutePath, format.extension))
            require(info.optBoolean("projector") == projector) {
                if (projector) "Select a multimodal projector." else "Add the language model first, then attach its projector."
            }
            modelRevision.incrementAndGet()
            runtimeClient.close()
            _loadState.value = GemmaLoadState.Disabled
            if (destination.exists()) {
                backupFile.delete()
                check(destination.renameTo(backupFile)) { "Could not back up the existing model." }
            }
            check(tempFile.renameTo(destination)) { "Could not install the AI model." }
            backupFile.delete()
            if (!projector) {
                profiles.setModelName(destination.absolutePath, info.optString("name"))
                if (selectForTranslation) appPreference.gemma_translation_model_path_preference = destination.absolutePath
            }
            lastErrorMessage = null
            destination.absolutePath
        }.onFailure {
            tempFile.delete()
            if (!destination.exists() && backupFile.exists()) backupFile.renameTo(destination)
        }.getOrThrow()
      }
    }

    suspend fun initializeIfEnabled(forceReload: Boolean = false): Boolean {
        return initializeMutex.withLock {
            if (!appPreference.enable_gemma_translation_preference) {
                runtimeClient.close()
                lastErrorMessage = null
                _loadState.value = GemmaLoadState.Disabled
                return@withLock false
            }
            if (Build.SUPPORTED_64_BIT_ABIS.isEmpty()) {
                runtimeClient.close()
                _loadState.value = GemmaLoadState.UnsupportedAbi
                return@withLock false
            }

            val modelFile = resolveModelFile()
            if (modelFile == null) {
                runtimeClient.close()
                _loadState.value = GemmaLoadState.MissingModel
                return@withLock false
            }
            val current = _loadState.value
            if (!forceReload && current is GemmaLoadState.Ready && current.modelPath == modelFile.absolutePath) {
                return@withLock true
            }

            val backend = appPreference.gemma_translation_backend_preference
            val revision = modelRevision.incrementAndGet()
            runtimeClient.cancelActive()
            _loadState.value = GemmaLoadState.Loading(backend)
            runCatching {
                validateModelFile(modelFile)
                runtimeClient.initialize(
                    modelPath = modelFile.absolutePath,
                    backend = backend,
                    projectorPath = profiles.projectorPath(modelFile.absolutePath),
                )
            }.onSuccess { resolvedBackend ->
                if (revision != modelRevision.get()) {
                    runtimeClient.close()
                    return@withLock false
                }
                val backendLabel = when (resolvedBackend) {
                    "CPU_FALLBACK" -> context.getString(
                        R.string.gemma_translation_backend_runtime_cpu_fallback,
                    )
                    "NPU_CPU_FALLBACK" -> context.getString(
                        R.string.ai_npu_cpu_fallback,
                    )
                    "NPU" -> context.getString(R.string.ai_backend_npu)
                    "GPU" -> context.getString(R.string.gemma_translation_backend_runtime_gpu)
                    else -> context.getString(R.string.gemma_translation_backend_runtime_cpu)
                }
                lastErrorMessage = null
                profiles.setModelName(modelFile.absolutePath, runtimeClient.modelName)
                _loadState.value = GemmaLoadState.Ready(backendLabel, modelFile.absolutePath, runtimeClient.modalityFlags)
                return@withLock true
            }.onFailure { error ->
                val message = error.rootMessage()
                Timber.e(error, "Gemma remote initialization failed")
                lastErrorMessage = message
                _loadState.value = GemmaLoadState.Failed(message)
            }
            false
        }
    }

    suspend fun disable() {
        modelRevision.incrementAndGet()
        runtimeClient.cancelActive()
        initializeMutex.withLock {
            runtimeClient.close()
            lastErrorMessage = null
            _loadState.value = GemmaLoadState.Disabled
        }
    }

    suspend fun translate(text: String): String {
        require(text.isNotBlank()) { "The selected candidate is empty." }
        ensureEngineReady()
        val target = TranslationTargetLanguage.fromPreference(
            appPreference.gemma_translation_target_language_preference,
        )
        return generateForCurrentModel { runtimeClient.generateTranslation(text, buildTranslationInstruction(target), target.languageCode) }
    }

    suspend fun runCustomPrompt(
        text: String,
        promptTitle: String,
        promptBody: String,
    ): String {
        require(text.isNotBlank()) { "The selected candidate is empty." }
        require(promptBody.isNotBlank()) { "The AI prompt is empty." }
        ensureEngineReady()
        return generateForCurrentModel { runtimeClient.generateText(text, GemmaPromptBuilder.buildInstruction(promptBody)) }
    }

    suspend fun runMediaPrompt(
        prompt: String,
        mediaPath: String,
        mediaType: GemmaMediaType,
    ): String {
        require(mediaType != GemmaMediaType.TEXT) { "A media type is required." }
        require(prompt.isNotBlank()) { "The AI action instruction is empty." }
        val file = File(mediaPath)
        require(file.isFile) { "Selected media is no longer available." }
        ensureEngineReady()
        val selected = selectedModel() ?: error("No AI model is selected.")
        if (!selected.descriptor.supports(mediaType)) {
            throw IllegalStateException(
                "${selected.descriptor.displayName} does not support ${mediaType.name.lowercase()} input.",
            )
        }
        return generateForCurrentModel { runtimeClient.generate(prompt, file.absolutePath, mediaType) }.trim()
    }

    fun cancelActiveTranslation() {
        runtimeClient.cancelActive()
    }

    internal suspend fun generateFloatingText(text: String, instruction: String): String? {
        check(_loadState.value is GemmaLoadState.Ready) { "AI is not ready." }
        val revision = modelRevision.get()
        val result = runtimeClient.generatePreview(
            text, GemmaPromptBuilder.buildInstruction(instruction),
        )
        return result.takeIf { revision == modelRevision.get() && _loadState.value is GemmaLoadState.Ready }
    }

    internal fun cancelFloatingText() = runtimeClient.cancelPreview()

    private suspend fun generateForCurrentModel(generate: suspend () -> String): String {
        check(_loadState.value is GemmaLoadState.Ready) { "AI model is not ready." }
        val revision = modelRevision.get()
        val result = generate()
        check(revision == modelRevision.get() && _loadState.value is GemmaLoadState.Ready) { "The selected AI model changed." }
        return result
    }

    private suspend fun ensureEngineReady() {
        if (_loadState.value is GemmaLoadState.Ready) return
        if (initializeIfEnabled(forceReload = false) && _loadState.value is GemmaLoadState.Ready) return
        throw IllegalStateException(
            lastErrorMessage ?: context.getString(R.string.gemma_translation_model_summary_missing),
        )
    }

    private fun resolveModelFile(): File? {
        val preferredPath = appPreference.gemma_translation_model_path_preference
        if (preferredPath.isNotBlank()) {
            File(preferredPath).takeIf(File::isFile)?.let { return it }
            appPreference.gemma_translation_model_path_preference = ""
        }
        val first = modelDirectories()
            .asSequence()
            .flatMap { directory -> directory.listFiles().orEmpty().asSequence() }
            .filter { it.isFile && AiModelFormat.fromFilename(it.name) != null }
            .sortedBy { it.name.lowercase() }
            .firstOrNull()
        if (first != null) appPreference.gemma_translation_model_path_preference = first.absolutePath
        return first
    }

    private fun modelDirectory(): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, MODEL_DIR_NAME).apply { mkdirs() }
    }

    private fun modelDirectories(): List<File> {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return listOf(
            modelDirectory(),
            File(base, LEGACY_MODEL_DIR_NAME),
        ).distinctBy { it.absolutePath }
    }

    private fun resolveDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index)?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/').orEmpty()
    }

    private fun validateImportedModelDisplayName(displayName: String) {
        require(AiModelFormat.fromFilename(displayName) != null) {
            context.getString(R.string.gemma_translation_model_import_invalid_extension)
        }
    }

    private fun sanitizeModelFilename(displayName: String): String {
        val format = requireNotNull(AiModelFormat.fromFilename(displayName))
        val stem = displayName.substringBefore('?').substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9._-]"), "_").take(MAX_FILENAME_LENGTH)
        return "${stem.ifBlank { "model" }}.${format.extension}"
    }

    private fun validateModelFile(file: File, format: AiModelFormat = requireNotNull(AiModelFormat.fromFilename(file.name))) {
        require(file.isFile && file.length() >= MIN_MODEL_BYTES) {
            context.getString(R.string.gemma_translation_model_import_invalid_size)
        }
        val header = file.inputStream().buffered().use { it.readAtMost(HEADER_BYTES) }
        val isGguf = header.take(4).toByteArray().toString(Charsets.US_ASCII) == "GGUF"
        require(if (format == AiModelFormat.GGUF) isGguf else !isGguf && !looksLikeUnsupportedFile(header)) {
            context.getString(R.string.gemma_translation_model_import_invalid_content)
        }
    }

    private fun looksLikeUnsupportedFile(header: ByteArray): Boolean {
        if (header.size >= 4 && header[0] == 'G'.code.toByte() && header[1] == 'G'.code.toByte() &&
            header[2] == 'U'.code.toByte() && header[3] == 'F'.code.toByte()
        ) return true
        val ascii = header.toString(Charsets.US_ASCII).trimStart()
        return ascii.startsWith("PK") || ascii.startsWith("{") || ascii.startsWith("[") ||
            ascii.startsWith("<") || ascii.startsWith("%PDF") ||
            ascii.startsWith("version https://git-lfs.github.com/spec")
    }

    private fun InputStream.readAtMost(maxBytes: Int): ByteArray {
        val buffer = ByteArray(maxBytes)
        var offset = 0
        while (offset < maxBytes) {
            val read = read(buffer, offset, maxBytes - offset)
            if (read <= 0) break
            offset += read
        }
        return buffer.copyOf(offset)
    }

    private fun buildTranslationInstruction(targetLanguage: TranslationTargetLanguage): String =
        GemmaPromptBuilder.buildInstruction("""
            Detect the source language and translate the input into ${targetLanguage.promptLanguageName} (${targetLanguage.languageCode}).
            If it is already primarily ${targetLanguage.promptLanguageName}, return it unchanged.
        """.trimIndent())

    private fun Throwable.rootMessage(): String {
        val root = generateSequence(this) { it.cause }.last()
        return root.localizedMessage?.takeIf { it.isNotBlank() }
            ?: root.javaClass.simpleName
    }

    companion object {
        // These IDs are persisted in candidate/UI behavior and must not overlap KanaKanjiEngine
        // candidate types (including 24..27).
        const val TRANSLATED_CANDIDATE_TYPE = 41
        const val PROMPT_RESULT_CANDIDATE_TYPE = 42
        const val SELECTION_TRANSLATE_ACTION_CANDIDATE_TYPE = 43
        const val SELECTION_PROMPT_ACTION_CANDIDATE_TYPE = 44

        private const val MODEL_DIR_NAME = "gemma"
        private const val LEGACY_MODEL_DIR_NAME = "models"
        private const val MIN_MODEL_BYTES = 1_048_576L
        private const val HEADER_BYTES = 64
        private const val MAX_FILENAME_LENGTH = 180
    }
}
