package com.kazumaproject.markdownhelperkeyboard.gemma.runtime

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.kazumaproject.markdownhelperkeyboard.ai.AiRuntimeBackend
import com.kazumaproject.markdownhelperkeyboard.ai.GgufRuntimeBackend
import com.kazumaproject.markdownhelperkeyboard.ai.LiteRtRuntimeBackend
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Owns both AI backends in :gemma. The persisted component name remains compatible. */
class GemmaRuntimeService : Service() {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "AiRuntimeActor") }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    @Volatile private var runtimeBackend: AiRuntimeBackend? = null
    @Volatile private var activeRequestId = NO_REQUEST

    private val binder = object : IGemmaRuntime.Stub() {
        // Retained for existing internal clients; capabilities now come from model metadata.
        override fun initialize(modelPath: String, backend: String, modalityFlags: Int, callback: IGemmaRuntimeCallback) {
            initializeModel(modelPath, backend, "", callback)
        }

        override fun initializeModel(modelPath: String, backend: String, projectorPath: String, callback: IGemmaRuntimeCallback) {
            scope.launch { initializeEngine(modelPath, backend, projectorPath, callback) }
        }

        override fun inspect(requestId: Long, modelPath: String, format: String, callback: IGemmaRuntimeCallback) {
            scope.launch {
                runCatching { newBackend(format).use { it.inspect(modelPath).encode() } }
                    .onSuccess { callback.result(requestId, it) }
                    .onFailure { callback.error(requestId, it) }
            }
        }

        override fun generate(requestId: Long, prompt: String, mediaPath: String, mediaType: Int, callback: IGemmaRuntimeCallback) {
            scope.launch { generateResponse(requestId, prompt, mediaPath, mediaType, callback) }
        }

        override fun generatePreview(requestId: Long, prompt: String, instruction: String, callback: IGemmaRuntimeCallback) {
            scope.launch { generateResponse(requestId, prompt, "", MEDIA_TEXT, callback, true, instruction) }
        }

        override fun generateText(requestId: Long, text: String, instruction: String, callback: IGemmaRuntimeCallback) {
            scope.launch { generateResponse(requestId, text, "", MEDIA_TEXT, callback, false, instruction) }
        }

        override fun generateTranslation(requestId: Long, text: String, instruction: String, targetLanguage: String, callback: IGemmaRuntimeCallback) {
            scope.launch { generateResponse(requestId, text, "", MEDIA_TEXT, callback, false, instruction, targetLanguage) }
        }

        override fun cancel(requestId: Long) {
            if (activeRequestId == requestId) runtimeBackend?.cancel()
        }

        override fun closeEngine() { scope.launch { closeRuntime() } }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        runtimeBackend?.cancel()
        scope.cancel()
        executor.execute {
            closeRuntime()
            dispatcher.close()
            executor.shutdown()
        }
        super.onDestroy()
    }

    private fun newBackend(format: String): AiRuntimeBackend = when (format.lowercase()) {
        "litertlm" -> LiteRtRuntimeBackend(this)
        "gguf" -> GgufRuntimeBackend()
        else -> error("Select a .litertlm or .gguf model.")
    }

    private fun initializeEngine(path: String, backend: String, projectorPath: String, callback: IGemmaRuntimeCallback) {
        callback.state("PROCESS", android.os.Process.myPid().toString())
        callback.state("LOADING", backend)
        closeRuntime()
        var candidate: AiRuntimeBackend? = null
        runCatching {
            require(File(path).isFile) { "Model file is missing." }
            candidate = newBackend(File(path).extension)
            val resolved = requireNotNull(candidate).initialize(path, backend, projectorPath)
            runtimeBackend = candidate
            callback.state("MODEL_NAME", requireNotNull(candidate).inspect(path).name)
            callback.state("CAPABILITIES", requireNotNull(candidate).modalityFlags.toString())
            callback.state("READY", resolved)
        }.onFailure {
            candidate?.close()
            runtimeBackend = null
            callback.error(0L, it)
        }
    }

    private fun generateResponse(
        requestId: Long, prompt: String, mediaPath: String, mediaType: Int,
        callback: IGemmaRuntimeCallback, preview: Boolean = false, instruction: String = "",
        targetLanguage: String = "",
    ) {
        runCatching {
            val backend = requireNotNull(runtimeBackend) { "AI model is not ready." }
            require(mediaType in MEDIA_TEXT..MEDIA_AUDIO) { "Unsupported media type." }
            if (mediaType != MEDIA_TEXT) {
                require(File(mediaPath).isFile) { "Selected media is no longer available." }
                val flag = if (mediaType == MEDIA_IMAGE) MODALITY_IMAGE else MODALITY_AUDIO
                require(backend.modalityFlags and flag != 0) { "The selected model does not support this media input." }
            }
            backend.resetCancellation()
            activeRequestId = requestId
            callback.state("RUNNING", requestId.toString())
            val result = backend.generate(prompt, mediaPath, mediaType, instruction, targetLanguage.lowercase(java.util.Locale.ROOT))
            (if (preview || mediaType == MEDIA_TEXT) result else result.trim())
                .ifBlank { error("AI returned an empty response.") }
        }.onSuccess { callback.result(requestId, it) }
            .onFailure { callback.error(requestId, it) }
        activeRequestId = NO_REQUEST
        callback.state("READY", "")
    }

    private fun closeRuntime() {
        runtimeBackend?.close()
        runtimeBackend = null
        activeRequestId = NO_REQUEST
    }

    private fun IGemmaRuntimeCallback.state(state: String, detail: String) { runCatching { onStateChanged(state, detail) } }
    private fun IGemmaRuntimeCallback.result(id: Long, text: String) { runCatching { onResult(id, text) } }
    private fun IGemmaRuntimeCallback.error(id: Long, error: Throwable) {
        val root = generateSequence(error) { it.cause }.last()
        runCatching { onError(id, root.localizedMessage ?: root.javaClass.simpleName) }
    }

    companion object {
        const val MODALITY_IMAGE = 1
        const val MODALITY_AUDIO = 2
        const val MEDIA_TEXT = 0
        const val MEDIA_IMAGE = 1
        const val MEDIA_AUDIO = 2
        private const val NO_REQUEST = -1L
    }
}
