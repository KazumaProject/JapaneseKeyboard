package com.kazumaproject.markdownhelperkeyboard.gemma

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaTranslationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreference: AppPreference,
) {

    private enum class BackendPreference(val preferenceValue: String) {
        Cpu("cpu"),
        GpuIfAvailable("gpu_if_available");

        companion object {
            fun fromPreference(value: String): BackendPreference {
                return values().firstOrNull { it.preferenceValue == value } ?: Cpu
            }
        }
    }

    private enum class ActiveBackend(@StringRes val summaryResId: Int) {
        Cpu(R.string.gemma_translation_backend_runtime_cpu),
        Gpu(R.string.gemma_translation_backend_runtime_gpu),
        CpuFallback(R.string.gemma_translation_backend_runtime_cpu_fallback),
    }

    private enum class TranslationTargetLanguage(
        val preferenceValue: String,
        val promptLanguageName: String,
        val languageCode: String
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
                return values().firstOrNull { it.preferenceValue == value } ?: English
            }
        }
    }

    private val initializeMutex = Mutex()

    @Volatile
    private var engineInstance: Any? = null

    @Volatile
    private var activeModelPath: String? = null

    @Volatile
    private var lastErrorMessage: String? = null

    @Volatile
    private var initialized = false

    @Volatile
    private var nativeLibraryLoaded = false

    @Volatile
    private var activeConversation: Any? = null

    @Volatile
    private var activeBackend: ActiveBackend? = null

    fun isTranslationAvailable(): Boolean {
        return appPreference.enable_gemma_translation_preference &&
            isSupportedAbi() &&
            initialized &&
            engineInstance != null
    }

    fun getModelSummary(): String {
        if (!isSupportedAbi()) {
            return context.getString(R.string.gemma_translation_model_summary_unsupported_abi)
        }

        val modelFile = resolveModelFile()
        val modelName = modelFile?.name ?: modelFileName()

        return when {
            isTranslationAvailable() -> {
                context.getString(
                    R.string.gemma_translation_model_summary_ready,
                    modelName,
                    context.getString(
                        activeBackend?.summaryResId ?: R.string.gemma_translation_backend_runtime_cpu
                    )
                )
            }

            lastErrorMessage != null && appPreference.enable_gemma_translation_preference -> {
                context.getString(
                    R.string.gemma_translation_model_summary_error,
                    lastErrorMessage.orEmpty()
                )
            }

            modelFile != null -> {
                context.getString(R.string.gemma_translation_model_summary_imported, modelName)
            }

            else -> context.getString(R.string.gemma_translation_model_summary_missing)
        }
    }

    suspend fun importModelFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val modelFile = canonicalModelFile()
        val tempFile = File(modelFile.parentFile, "${modelFile.name}.${System.currentTimeMillis()}.tmp")
        val backupFile = if (modelFile.exists()) {
            File(modelFile.parentFile, "${modelFile.name}.bak")
        } else {
            null
        }

        runCatching {
            validateImportedModelDisplayName(resolveDisplayName(uri))

            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open Gemma model uri: $uri" }
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            validateModelFile(tempFile)

            if (backupFile != null) {
                backupFile.delete()
                if (!modelFile.renameTo(backupFile)) {
                    throw IllegalStateException("Failed to back up the current Gemma model.")
                }
            }

            if (!tempFile.renameTo(modelFile)) {
                throw IllegalStateException("Failed to move imported Gemma model into place.")
            }

            backupFile?.delete()
            appPreference.gemma_translation_model_path_preference = modelFile.absolutePath
            lastErrorMessage = null
            modelFile.absolutePath
        }.onFailure {
            tempFile.delete()
            if (backupFile != null && backupFile.exists()) {
                backupFile.renameTo(modelFile)
            }
        }.getOrThrow()
    }

    suspend fun initializeIfEnabled(forceReload: Boolean = false): Boolean {
        return initializeMutex.withLock {
            if (!appPreference.enable_gemma_translation_preference) {
                closeLocked()
                lastErrorMessage = null
                return@withLock false
            }

            if (!isSupportedAbi()) {
                closeLocked()
                lastErrorMessage =
                    context.getString(R.string.gemma_translation_model_summary_unsupported_abi)
                return@withLock false
            }

            val modelFile = resolveModelFile()
            if (modelFile == null) {
                closeLocked()
                lastErrorMessage = context.getString(R.string.gemma_translation_model_summary_missing)
                return@withLock false
            }

            runCatching {
                validateModelFile(modelFile)
            }.onFailure { error ->
                closeLocked()
                lastErrorMessage = error.toUserVisibleMessage()
                return@withLock false
            }

            if (!forceReload && initialized && engineInstance != null && activeModelPath == modelFile.absolutePath) {
                return@withLock true
            }

            closeLocked()

            try {
                val (createdEngine, resolvedBackend) = createEngineForPreference(
                    modelPath = modelFile.absolutePath,
                    backendPreference = BackendPreference.fromPreference(
                        appPreference.gemma_translation_backend_preference
                    )
                )
                engineInstance = createdEngine
                activeBackend = resolvedBackend

                activeModelPath = modelFile.absolutePath
                initialized = true
                lastErrorMessage = null
                true
            } catch (error: Throwable) {
                Timber.e(error, "Gemma initialization failed.")
                closeLocked()
                lastErrorMessage = error.toUserVisibleMessage()
                false
            }
        }
    }

    suspend fun disable() {
        initializeMutex.withLock {
            closeLocked()
            lastErrorMessage = null
        }
    }

    suspend fun translate(text: String): String = withContext(Dispatchers.Default) {
        if (text.isBlank()) {
            throw IllegalArgumentException("The selected candidate is empty.")
        }
        ensureEngineReady()
        val targetLanguage = TranslationTargetLanguage.fromPreference(
            appPreference.gemma_translation_target_language_preference
        )
        runPromptAndSanitize(buildTranslationPrompt(text, targetLanguage))
    }

    suspend fun runCustomPrompt(
        text: String,
        promptTitle: String,
        promptBody: String
    ): String = withContext(Dispatchers.Default) {
        if (text.isBlank()) {
            throw IllegalArgumentException("The selected candidate is empty.")
        }
        if (promptBody.isBlank()) {
            throw IllegalArgumentException("The Gemma prompt is empty.")
        }
        ensureEngineReady()
        runPromptAndSanitize(buildCustomPrompt(text, promptTitle, promptBody))
    }

    private suspend fun ensureEngineReady() {
        if (initialized && engineInstance != null) return

        val initializedNow = initializeIfEnabled(forceReload = false)
        if (initializedNow && engineInstance != null) return

        val message = lastErrorMessage
            ?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.gemma_translation_model_summary_missing)
        throw IllegalStateException(message)
    }

    fun cancelActiveTranslation() {
        val conversation = activeConversation ?: return
        activeConversation = null
        runCatching {
            conversation.javaClass.getMethod("cancelProcess").invoke(conversation)
        }.onFailure {
            Timber.w(it, "Failed to cancel active Gemma conversation.")
        }
        closeQuietly(conversation)
    }

    private fun createEngineForPreference(
        modelPath: String,
        backendPreference: BackendPreference
    ): Pair<Any, ActiveBackend> {
        return when (backendPreference) {
            BackendPreference.Cpu -> {
                createEngine(
                    modelPath = modelPath,
                    textBackendName = "CPU",
                    visionBackendName = "CPU",
                    audioBackendName = "CPU"
                ) to ActiveBackend.Cpu
            }

            BackendPreference.GpuIfAvailable -> {
                runCatching {
                    createEngine(
                        modelPath = modelPath,
                        textBackendName = "GPU",
                        visionBackendName = "CPU",
                        audioBackendName = "CPU"
                    ) to ActiveBackend.Gpu
                }.getOrElse { gpuError ->
                    Timber.w(
                        gpuError,
                        "GPU Gemma initialization failed. Retrying with GPU_ARTISAN backend."
                    )
                    runCatching {
                        createEngine(
                            modelPath = modelPath,
                            textBackendName = "GPU_ARTISAN",
                            visionBackendName = "CPU",
                            audioBackendName = "CPU"
                        ) to ActiveBackend.Gpu
                    }.getOrElse { gpuArtisanError ->
                        Timber.w(
                            gpuArtisanError,
                            "GPU and GPU_ARTISAN Gemma initialization failed. Falling back to CPU."
                        )
                        createEngine(
                            modelPath = modelPath,
                            textBackendName = "CPU",
                            visionBackendName = "CPU",
                            audioBackendName = "CPU"
                        ) to ActiveBackend.CpuFallback
                    }
                }
            }
        }
    }

    private fun createEngine(
        modelPath: String,
        textBackendName: String,
        visionBackendName: String,
        audioBackendName: String
    ): Any {
        ensureNativeLibraryLoaded()
        val backendClass = Class.forName(BACKEND_CLASS)
        val engineConfigClass = Class.forName(ENGINE_CONFIG_CLASS)
        val textBackendValue = createBackendInstance(textBackendName)
        val visionBackendValue = createBackendInstance(visionBackendName)
        val audioBackendValue = createBackendInstance(audioBackendName)
        val config = engineConfigClass.getConstructor(
            String::class.java,
            backendClass,
            backendClass,
            backendClass,
            Integer::class.java,
            String::class.java,
        ).newInstance(
            modelPath,
            textBackendValue,
            visionBackendValue,
            audioBackendValue,
            null,
            context.cacheDir.absolutePath,
        )

        return Class.forName(ENGINE_CLASS)
            .getConstructor(engineConfigClass)
            .newInstance(config)
            .apply {
                javaClass.getMethod("initialize").invoke(this)
            }
    }

    private fun createBackendInstance(backendName: String): Any {
        val backendImplementationClass = when (backendName) {
            "CPU" -> Class.forName(BACKEND_CPU_CLASS)
            "GPU_ARTISAN" -> Class.forName(BACKEND_GPU_ARTISAN_CLASS)
            "GPU" -> Class.forName(BACKEND_GPU_CLASS)
            "NPU" -> Class.forName(BACKEND_NPU_CLASS)
            else -> error("Unsupported backend: $backendName")
        }

        return backendImplementationClass.getConstructor().newInstance()
    }

    private fun ensureNativeLibraryLoaded() {
        if (nativeLibraryLoaded) return

        synchronized(this) {
            if (nativeLibraryLoaded) return

            runCatching {
                System.loadLibrary("litertlm_jni")
            }.onFailure {
                Timber.w(it, "System.loadLibrary(litertlm_jni) failed or was already handled.")
            }

            val loaderClass = Class.forName(NATIVE_LIBRARY_LOADER_CLASS)
            val instance = loaderClass.getField("INSTANCE").get(null)
            loaderClass.getMethod("load").invoke(instance)
            nativeLibraryLoaded = true
        }
    }

    private fun createConversation(engine: Any): Any {
        val conversationConfigClass = Class.forName(CONVERSATION_CONFIG_CLASS)
        val conversationConfig = conversationConfigClass.getConstructor().newInstance()
        return engine.javaClass
            .getMethod("createConversation", conversationConfigClass)
            .invoke(engine, conversationConfig)
    }

    private fun sendPrompt(conversation: Any, prompt: String): Any {
        return conversation.javaClass
            .getMethod("sendMessage", String::class.java, Map::class.java)
            .invoke(conversation, prompt, emptyMap<String, Any>())
    }

    private fun extractTextContents(message: Any): String {
        val textContentClass = Class.forName(CONTENT_TEXT_CLASS)
        val contentsContainer = message.javaClass
            .getMethod("getContents")
            .invoke(message)
            ?: return ""
        val contents = contentsContainer.javaClass
            .getMethod("getContents")
            .invoke(contentsContainer) as? List<*>
            ?: emptyList<Any>()

        return contents
            .filter { textContentClass.isInstance(it) }
            .joinToString(separator = "") { content ->
                textContentClass.getMethod("getText").invoke(content) as? String ?: ""
            }
    }

    private fun closeLocked() {
        initialized = false
        activeModelPath = null
        activeBackend = null
        closeQuietly(engineInstance)
        engineInstance = null
    }

    private fun closeQuietly(target: Any?) {
        if (target == null) return
        runCatching {
            target.javaClass.getMethod("close").invoke(target)
        }.onFailure {
            Timber.w(it, "Failed to close LiteRT-LM resource cleanly.")
        }
    }

    private fun resolveModelFile(): File? {
        val preferredPath = appPreference.gemma_translation_model_path_preference
        if (preferredPath.isNotBlank()) {
            val preferredFile = File(preferredPath)
            if (preferredFile.exists()) {
                return preferredFile
            }
            appPreference.gemma_translation_model_path_preference = ""
        }

        val found = modelFileCandidates()
            .map { File(modelDirectory(), it) }
            .firstOrNull { it.exists() }

        if (found != null) {
            appPreference.gemma_translation_model_path_preference = found.absolutePath
        }

        return found
    }

    private fun canonicalModelFile(): File {
        return File(modelDirectory(), modelFileName())
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index < 0) return@use null
            cursor.getString(index)
        } ?: uri.lastPathSegment
    }

    private fun validateImportedModelDisplayName(displayName: String?) {
        val name = displayName?.trim().orEmpty()
        if (name.isEmpty()) return

        val normalized = name.lowercase(Locale.ROOT)
        require(normalized.endsWith(MODEL_EXTENSION)) {
            context.getString(R.string.gemma_translation_model_import_invalid_extension)
        }
        require(normalized.contains(SUPPORTED_MODEL_NAME_FRAGMENT)) {
            context.getString(R.string.gemma_translation_model_import_invalid_name)
        }
    }

    private fun validateModelFile(modelFile: File) {
        require(modelFile.exists()) {
            context.getString(R.string.gemma_translation_model_summary_missing)
        }
        require(modelFile.isFile) {
            context.getString(R.string.gemma_translation_model_import_invalid_name)
        }
        require(modelFile.length() >= MIN_MODEL_BYTES) {
            context.getString(R.string.gemma_translation_model_import_invalid_size)
        }

        val header = modelFile.inputStream().use { input ->
            input.readNBytes(MODEL_HEADER_SAMPLE_SIZE)
        }
        require(header.isNotEmpty()) {
            context.getString(R.string.gemma_translation_model_import_invalid_size)
        }
        require(!looksLikeUnsupportedModelFile(header)) {
            context.getString(R.string.gemma_translation_model_import_invalid_content)
        }
    }

    private fun looksLikeUnsupportedModelFile(header: ByteArray): Boolean {
        return startsWith(header, ZIP_HEADER) ||
            startsWith(header, PDF_HEADER) ||
            startsWith(header, GGUF_HEADER) ||
            startsWithAscii(header, "{") ||
            startsWithAscii(header, "[") ||
            startsWithAscii(header, "\"") ||
            startsWithAscii(header, "<") ||
            startsWithAscii(header, "version https://git-lfs.github.com/spec")
    }

    private fun startsWith(header: ByteArray, prefix: ByteArray): Boolean {
        if (header.size < prefix.size) return false
        return prefix.indices.all { index -> header[index] == prefix[index] }
    }

    private fun startsWithAscii(header: ByteArray, prefix: String): Boolean {
        val value = header.toString(Charsets.US_ASCII).trimStart()
        return value.startsWith(prefix)
    }

    private fun modelDirectory(): File {
        val externalBase = context.getExternalFilesDir(null)
        val baseDir = externalBase ?: context.filesDir
        return File(baseDir, MODEL_DIR_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private fun isSupportedAbi(): Boolean {
        return Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
    }

    private suspend fun runPromptAndSanitize(prompt: String): String {
        val activeEngine = engineInstance
            ?: throw IllegalStateException("Gemma translation model is not ready.")

        return withTimeout(60_000L) {
            val conversation = createConversation(activeEngine)
            activeConversation = conversation
            try {
                val response = sendPrompt(conversation, prompt)
                val translated = extractTextContents(response).trim()
                sanitizeOutput(translated)
            } catch (error: Throwable) {
                if (!currentCoroutineContext().isActive || activeConversation !== conversation) {
                    throw kotlinx.coroutines.CancellationException(
                        "Gemma translation was cancelled.",
                        error
                    )
                }
                throw error
            } finally {
                if (activeConversation === conversation) {
                    activeConversation = null
                }
                closeQuietly(conversation)
            }
        }
    }

    private fun buildTranslationPrompt(
        text: String,
        targetLanguage: TranslationTargetLanguage
    ): String {
        return """
            You are a translation engine for IME candidates.
            First detect the source language automatically from the input text.
            Then translate the input into ${targetLanguage.promptLanguageName} (${targetLanguage.languageCode}).
            If the input is already primarily ${targetLanguage.promptLanguageName}, return the original text unchanged.
            Never choose a different target language.
            Preserve names, emoji, markdown punctuation, spacing, and formatting where possible.
            Return only the final translated text with no explanation, no language labels, and no quotes.
            Text: $text
        """.trimIndent()
    }

    private fun buildCustomPrompt(
        text: String,
        promptTitle: String,
        promptBody: String
    ): String {
        val sanitizedTitle = promptTitle.trim().ifEmpty { "Custom prompt" }
        val sanitizedPrompt = promptBody.trim()
        return """
            You are an IME candidate transformation engine.
            Apply the following user-defined instruction to the input text.
            Return only the final transformed text with no explanation, no bullets, no quotes, and no surrounding labels.
            Preserve markdown punctuation, emoji, whitespace, and formatting where possible unless the instruction asks you to change them.
            Prompt title: $sanitizedTitle
            User instruction:
            $sanitizedPrompt

            Input text:
            $text
        """.trimIndent()
    }

    private fun sanitizeOutput(output: String): String {
        val noFence = output
            .replace("```", "")
            .trim()
        val firstMeaningfulLine = noFence
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
        return firstMeaningfulLine.trim().trim('"', '\'', '「', '」')
    }

    private fun Throwable.toUserVisibleMessage(): String {
        val localized = localizedMessage?.trim().orEmpty()
        if (localized.isNotEmpty()) return localized

        val message = message?.trim().orEmpty()
        if (message.isNotEmpty()) return message

        return javaClass.simpleName
    }

    private fun modelFileName(): String = modelFileCandidates().first()

    private fun modelFileCandidates(): List<String> = listOf(
        "gemma-4-E2B-it-litert-lm.litertlm",
        "gemma-4-e2b-it-litert-lm.litertlm",
        "gemma-4-E2B-it.litertlm",
        "gemma-4-e2b-it.litertlm",
    )

    companion object {
        const val TRANSLATED_CANDIDATE_TYPE = 41
        const val PROMPT_RESULT_CANDIDATE_TYPE = 42
        const val SELECTION_TRANSLATE_ACTION_CANDIDATE_TYPE = 43
        const val SELECTION_PROMPT_ACTION_CANDIDATE_TYPE = 44
        private const val MODEL_DIR_NAME = "models"
        private const val MODEL_EXTENSION = ".litertlm"
        private const val SUPPORTED_MODEL_NAME_FRAGMENT = "gemma-4-e2b-it"
        private const val MIN_MODEL_BYTES = 1_048_576L
        private const val MODEL_HEADER_SAMPLE_SIZE = 64
        private val ZIP_HEADER = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        private val PDF_HEADER = "%PDF".toByteArray(Charsets.US_ASCII)
        private val GGUF_HEADER = "GGUF".toByteArray(Charsets.US_ASCII)
        private const val BACKEND_CLASS = "com.google.ai.edge.litertlm.Backend"
        private const val BACKEND_CPU_CLASS = "com.google.ai.edge.litertlm.Backend\$CPU"
        private const val BACKEND_GPU_ARTISAN_CLASS =
            "com.google.ai.edge.litertlm.Backend\$GpuArtisan"
        private const val BACKEND_GPU_CLASS = "com.google.ai.edge.litertlm.Backend\$GPU"
        private const val BACKEND_NPU_CLASS = "com.google.ai.edge.litertlm.Backend\$NPU"
        private const val ENGINE_CONFIG_CLASS = "com.google.ai.edge.litertlm.EngineConfig"
        private const val ENGINE_CLASS = "com.google.ai.edge.litertlm.Engine"
        private const val CONVERSATION_CONFIG_CLASS = "com.google.ai.edge.litertlm.ConversationConfig"
        private const val CONTENT_TEXT_CLASS = "com.google.ai.edge.litertlm.Content\$Text"
        private const val NATIVE_LIBRARY_LOADER_CLASS =
            "com.google.ai.edge.litertlm.NativeLibraryLoader"
    }
}
