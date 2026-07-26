package com.kazumaproject.markdownhelperkeyboard.gemma.runtime

import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.io.File
import java.lang.reflect.Array as ReflectArray
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns all LiteRT-LM native state in the isolated app process named `:gemma`.
 * Binder calls never run model work directly; a single actor dispatcher serializes it.
 */
class GemmaRuntimeService : Service() {

    private val actorExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "GemmaRuntimeActor")
    }
    private val actorDispatcher: CoroutineDispatcher = actorExecutor.asCoroutineDispatcher()
    private val actorScope = CoroutineScope(SupervisorJob() + actorDispatcher)

    @Volatile
    private var engine: Any? = null

    @Volatile
    private var conversation: Any? = null

    @Volatile
    private var activeRequestId: Long = NO_REQUEST

    private val binder = object : IGemmaRuntime.Stub() {
        override fun initialize(
            modelPath: String,
            backend: String,
            modalityFlags: Int,
            callback: IGemmaRuntimeCallback,
        ) {
            actorScope.launch {
                initializeEngine(modelPath, backend, modalityFlags, callback)
            }
        }

        override fun generate(
            requestId: Long,
            prompt: String,
            mediaPath: String,
            mediaType: Int,
            callback: IGemmaRuntimeCallback,
        ) {
            actorScope.launch {
                generateResponse(requestId, prompt, mediaPath, mediaType, callback)
            }
        }

        override fun cancel(requestId: Long) {
            if (activeRequestId != requestId) return
            runCatching { conversation?.invokeNoArgs("cancelProcess") }
                .onFailure { Timber.w(it, "Failed to cancel Gemma request %s", requestId) }
        }

        override fun closeEngine() {
            actorScope.launch { closeRuntime() }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        closeRuntime()
        actorScope.cancel()
        actorDispatcher.closeIfPossible()
        actorExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun initializeEngine(
        modelPath: String,
        requestedBackend: String,
        modalityFlags: Int,
        callback: IGemmaRuntimeCallback,
    ) {
        callback.safeState(STATE_PROCESS, android.os.Process.myPid().toString())
        callback.safeState(STATE_LOADING, requestedBackend)
        closeRuntime()

        val modelFile = File(modelPath)
        if (!modelFile.isFile) {
            callback.safeError(INITIALIZE_REQUEST, "Model file is missing.")
            return
        }

        val primaryResult = runCatching {
            createAndInitializeEngine(modelPath, requestedBackend, modalityFlags)
        }
        val initialized = primaryResult.getOrNull()
        if (initialized != null) {
            engine = initialized
            callback.safeState(STATE_READY, requestedBackend.runtimeLabel())
            return
        }

        if (requestedBackend == BACKEND_GPU) {
            Timber.w(primaryResult.exceptionOrNull(), "GPU Gemma initialization failed; trying CPU")
            val fallbackResult = runCatching {
                createAndInitializeEngine(modelPath, BACKEND_CPU, modalityFlags)
            }
            val fallback = fallbackResult.getOrNull()
            if (fallback != null) {
                engine = fallback
                callback.safeState(STATE_READY, BACKEND_CPU_FALLBACK)
                return
            }
            callback.safeError(
                INITIALIZE_REQUEST,
                fallbackResult.exceptionOrNull().toRuntimeMessage(),
            )
            return
        }

        callback.safeError(INITIALIZE_REQUEST, primaryResult.exceptionOrNull().toRuntimeMessage())
    }

    private fun createAndInitializeEngine(
        modelPath: String,
        requestedBackend: String,
        modalityFlags: Int,
    ): Any {
        val supportsImages = modalityFlags and MODALITY_IMAGE != 0
        val supportsAudio = modalityFlags and MODALITY_AUDIO != 0
        val backendClass = Class.forName(CLASS_BACKEND)
        val textBackend = newBackend(requestedBackend)
        val visionBackend = if (supportsImages) textBackend else null
        val audioBackend = if (supportsAudio) newBackend(BACKEND_CPU) else null
        val engineConfigClass = Class.forName(CLASS_ENGINE_CONFIG)
        val config = engineConfigClass.getConstructor(
            String::class.java,
            backendClass,
            backendClass,
            backendClass,
            Integer::class.java,
            Integer::class.java,
            String::class.java,
        ).newInstance(
            modelPath,
            textBackend,
            visionBackend,
            audioBackend,
            null,
            if (supportsImages) Integer.valueOf(MAX_IMAGES) else null,
            cacheDir.absolutePath,
        )
        val candidate = Class.forName(CLASS_ENGINE)
            .getConstructor(engineConfigClass)
            .newInstance(config)
        return runCatching {
            candidate.invokeNoArgs("initialize")
            candidate
        }.onFailure {
            runCatching { candidate.invokeNoArgs("close") }
        }.getOrThrow()
    }

    private fun generateResponse(
        requestId: Long,
        prompt: String,
        mediaPath: String,
        mediaType: Int,
        callback: IGemmaRuntimeCallback,
    ) {
        val activeEngine = engine
        if (activeEngine == null || activeEngine.invokeNoArgs("isInitialized") != true) {
            callback.safeError(requestId, "Gemma model is not ready.")
            return
        }

        if (mediaType != MEDIA_TEXT && !File(mediaPath).isFile) {
            callback.safeError(requestId, "Selected media is no longer available.")
            return
        }

        callback.safeState(STATE_RUNNING, requestId.toString())
        activeRequestId = requestId
        val conversationConfigClass = Class.forName(CLASS_CONVERSATION_CONFIG)
        val conversationConfig = conversationConfigClass.getConstructor().newInstance()
        val activeConversation = requireNotNull(activeEngine.javaClass
            .getMethod("createConversation", conversationConfigClass)
            .invoke(activeEngine, conversationConfig))
        conversation = activeConversation
        runCatching {
            val contents = createContents(prompt, mediaPath, mediaType)
            val response = requireNotNull(activeConversation.javaClass
                .getMethod("sendMessage", Class.forName(CLASS_CONTENTS), Map::class.java)
                .invoke(activeConversation, contents, emptyMap<String, Any>()))
            extractText(response)
                .trim()
                .ifEmpty { throw IllegalStateException("Gemma returned an empty response.") }
        }.onSuccess { result ->
            callback.safeResult(requestId, result)
        }.onFailure { error ->
            callback.safeError(requestId, error.toRuntimeMessage())
        }

        if (conversation === activeConversation) conversation = null
        activeConversation.invokeNoArgs("close")
        activeRequestId = NO_REQUEST
        callback.safeState(STATE_READY, "")
    }

    private fun closeRuntime() {
        runCatching { conversation?.invokeNoArgs("cancelProcess") }
        runCatching { conversation?.invokeNoArgs("close") }
        conversation = null
        activeRequestId = NO_REQUEST
        runCatching { engine?.invokeNoArgs("close") }
        engine = null
    }

    private fun newBackend(value: String): Any {
        val className = if (value == BACKEND_GPU) CLASS_BACKEND_GPU else CLASS_BACKEND_CPU
        return Class.forName(className).getConstructor().newInstance()
    }

    private fun String.runtimeLabel(): String = when (this) {
        BACKEND_GPU -> "GPU"
        else -> "CPU"
    }

    private fun Throwable?.toRuntimeMessage(): String {
        val root = generateSequence(this) { it.cause }.lastOrNull()
        return root?.localizedMessage?.takeIf { it.isNotBlank() }
            ?: root?.javaClass?.simpleName
            ?: "Unknown Gemma runtime error."
    }

    private fun createContents(prompt: String, mediaPath: String, mediaType: Int): Any {
        val contentsClass = Class.forName(CLASS_CONTENTS)
        val companion = contentsClass.getField("Companion").get(null)
        if (mediaType == MEDIA_TEXT) {
            return requireNotNull(
                companion.javaClass.getMethod("of", String::class.java).invoke(companion, prompt)
            )
        }

        val contentClass = Class.forName(CLASS_CONTENT)
        val mediaClassName = if (mediaType == MEDIA_IMAGE) {
            CLASS_IMAGE_FILE
        } else {
            CLASS_AUDIO_FILE
        }
        val media = Class.forName(mediaClassName).getConstructor(String::class.java)
            .newInstance(mediaPath)
        val text = Class.forName(CLASS_TEXT).getConstructor(String::class.java)
            .newInstance(prompt)
        val values = ReflectArray.newInstance(contentClass, 2)
        ReflectArray.set(values, 0, media)
        ReflectArray.set(values, 1, text)
        val arrayClass = values.javaClass
        return requireNotNull(
            companion.javaClass.getMethod("of", arrayClass).invoke(companion, values)
        )
    }

    private fun extractText(message: Any): String {
        val contents = message.invokeNoArgs("getContents") ?: return ""
        val values = contents.invokeNoArgs("getContents") as? List<*> ?: return ""
        return values.joinToString(separator = "") { content ->
            if (content?.javaClass?.name == CLASS_TEXT) {
                content.invokeNoArgs("getText") as? String ?: ""
            } else {
                ""
            }
        }
    }

    private fun Any.invokeNoArgs(name: String): Any? = javaClass.getMethod(name).invoke(this)

    private fun IGemmaRuntimeCallback.safeState(state: String, detail: String) {
        runCatching { onStateChanged(state, detail) }
    }

    private fun IGemmaRuntimeCallback.safeResult(requestId: Long, result: String) {
        runCatching { onResult(requestId, result) }
    }

    private fun IGemmaRuntimeCallback.safeError(requestId: Long, message: String) {
        runCatching { onError(requestId, message) }
    }

    private fun CoroutineDispatcher.closeIfPossible() {
        (this as? AutoCloseable)?.close()
    }

    companion object {
        const val MODALITY_IMAGE = 1
        const val MODALITY_AUDIO = 1 shl 1

        const val MEDIA_TEXT = 0
        const val MEDIA_IMAGE = 1
        const val MEDIA_AUDIO = 2

        private const val MAX_IMAGES = 8
        private const val NO_REQUEST = -1L
        private const val INITIALIZE_REQUEST = 0L

        private const val BACKEND_GPU = "gpu_if_available"
        private const val BACKEND_CPU = "cpu"
        private const val BACKEND_CPU_FALLBACK = "CPU_FALLBACK"

        private const val STATE_LOADING = "LOADING"
        private const val STATE_READY = "READY"
        private const val STATE_RUNNING = "RUNNING"
        private const val STATE_PROCESS = "PROCESS"

        private const val CLASS_BACKEND = "com.google.ai.edge.litertlm.Backend"
        private const val CLASS_BACKEND_CPU = "com.google.ai.edge.litertlm.Backend\$CPU"
        private const val CLASS_BACKEND_GPU = "com.google.ai.edge.litertlm.Backend\$GPU"
        private const val CLASS_ENGINE_CONFIG = "com.google.ai.edge.litertlm.EngineConfig"
        private const val CLASS_ENGINE = "com.google.ai.edge.litertlm.Engine"
        private const val CLASS_CONVERSATION_CONFIG =
            "com.google.ai.edge.litertlm.ConversationConfig"
        private const val CLASS_CONTENT = "com.google.ai.edge.litertlm.Content"
        private const val CLASS_CONTENTS = "com.google.ai.edge.litertlm.Contents"
        private const val CLASS_IMAGE_FILE = "com.google.ai.edge.litertlm.Content\$ImageFile"
        private const val CLASS_AUDIO_FILE = "com.google.ai.edge.litertlm.Content\$AudioFile"
        private const val CLASS_TEXT = "com.google.ai.edge.litertlm.Content\$Text"
    }
}
