package com.kazumaproject.markdownhelperkeyboard.gemma

import android.content.Context
import android.net.Uri
import android.os.Build
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaTranslationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreference: AppPreference,
) {

    private enum class TranslationDirection {
        JapaneseToEnglish,
        EnglishToJapanese,
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
                context.getString(R.string.gemma_translation_model_summary_ready, modelName)
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

        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open Gemma model uri: $uri" }
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        if (modelFile.exists()) {
            modelFile.delete()
        }
        if (!tempFile.renameTo(modelFile)) {
            tempFile.delete()
            throw IllegalStateException("Failed to move imported Gemma model into place.")
        }

        appPreference.gemma_translation_model_path_preference = modelFile.absolutePath
        lastErrorMessage = null
        modelFile.absolutePath
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

            if (!forceReload && initialized && engineInstance != null && activeModelPath == modelFile.absolutePath) {
                return@withLock true
            }

            closeLocked()

            try {
                // Gemma 4 multi-section .litertlm files can include audio/vision sections
                // with explicit CPU constraints. For the translation feature we prefer a
                // conservative CPU-only initialization to avoid native aborts on startup.
                engineInstance = createEngine(modelFile.absolutePath, "CPU")

                activeModelPath = modelFile.absolutePath
                initialized = true
                lastErrorMessage = null
                true
            } catch (error: Throwable) {
                Timber.e(error, "Gemma initialization failed.")
                closeLocked()
                lastErrorMessage = error.localizedMessage ?: error.javaClass.simpleName
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
        val direction = detectDirection(text)
            ?: throw IllegalArgumentException("The selected candidate could not be classified for translation.")

        val activeEngine = engineInstance
            ?: throw IllegalStateException("Gemma translation model is not ready.")

        withTimeout(60_000L) {
            val conversation = createConversation(activeEngine)
            activeConversation = conversation
            try {
                val response = sendPrompt(conversation, buildPrompt(text, direction))
                val translated = extractTextContents(response).trim()
                sanitizeOutput(translated)
            } catch (error: Throwable) {
                if (!currentCoroutineContext().isActive || activeConversation !== conversation) {
                    throw kotlinx.coroutines.CancellationException("Gemma translation was cancelled.", error)
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

    private fun createEngine(modelPath: String, backendName: String): Any {
        ensureNativeLibraryLoaded()
        val backendClass = Class.forName(BACKEND_CLASS)
        val engineConfigClass = Class.forName(ENGINE_CONFIG_CLASS)
        val backendValue = createBackendInstance(backendName)
        val config = engineConfigClass.getConstructor(
            String::class.java,
            backendClass,
            backendClass,
            backendClass,
            Integer::class.java,
            String::class.java,
        ).newInstance(
            modelPath,
            backendValue,
            backendValue,
            backendValue,
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

    private fun detectDirection(text: String): TranslationDirection? {
        val hasJapanese = JAPANESE_REGEX.containsMatchIn(text)
        val hasEnglish = ENGLISH_REGEX.containsMatchIn(text)
        return when {
            hasJapanese -> TranslationDirection.JapaneseToEnglish
            hasEnglish -> TranslationDirection.EnglishToJapanese
            else -> null
        }
    }

    private fun buildPrompt(text: String, direction: TranslationDirection): String {
        return when (direction) {
            TranslationDirection.JapaneseToEnglish -> """
                You are translating an IME candidate.
                Translate the following Japanese text into concise natural English.
                Return only the translated text.
                Text: $text
            """.trimIndent()

            TranslationDirection.EnglishToJapanese -> """
                You are translating an IME candidate.
                Translate the following English text into concise natural Japanese.
                Return only the translated text.
                Text: $text
            """.trimIndent()
        }
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

    private fun modelFileName(): String = modelFileCandidates().first()

    private fun modelFileCandidates(): List<String> = listOf(
        "gemma-4-E2B-it-litert-lm.litertlm",
        "gemma-4-e2b-it-litert-lm.litertlm",
        "gemma-4-E2B-it.litertlm",
        "gemma-4-e2b-it.litertlm",
    )

    companion object {
        const val TRANSLATED_CANDIDATE_TYPE = 41
        private const val MODEL_DIR_NAME = "models"
        private const val BACKEND_CLASS = "com.google.ai.edge.litertlm.Backend"
        private const val BACKEND_CPU_CLASS = "com.google.ai.edge.litertlm.Backend\$CPU"
        private const val BACKEND_GPU_CLASS = "com.google.ai.edge.litertlm.Backend\$GPU"
        private const val BACKEND_NPU_CLASS = "com.google.ai.edge.litertlm.Backend\$NPU"
        private const val ENGINE_CONFIG_CLASS = "com.google.ai.edge.litertlm.EngineConfig"
        private const val ENGINE_CLASS = "com.google.ai.edge.litertlm.Engine"
        private const val CONVERSATION_CONFIG_CLASS = "com.google.ai.edge.litertlm.ConversationConfig"
        private const val CONTENT_TEXT_CLASS = "com.google.ai.edge.litertlm.Content\$Text"
        private const val NATIVE_LIBRARY_LOADER_CLASS =
            "com.google.ai.edge.litertlm.NativeLibraryLoader"
        private val JAPANESE_REGEX = Regex("[\\p{InHiragana}\\p{InKatakana}\\p{IsHan}]")
        private val ENGLISH_REGEX = Regex("[A-Za-z]")
    }
}
