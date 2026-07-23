package com.kazumaproject.markdownhelperkeyboard.gemma.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Main-process client for the crash-isolated LiteRT-LM service. */
@Singleton
class GemmaRuntimeClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private data class InitializationConfig(
        val modelPath: String,
        val backend: String,
        val modalityFlags: Int,
    )

    private val connectionMutex = Mutex()
    private val operationMutex = Mutex()
    private val nextRequestId = AtomicLong(1L)

    @Volatile
    private var runtime: IGemmaRuntime? = null

    @Volatile
    private var binding: CompletableDeferred<IGemmaRuntime>? = null

    @Volatile
    private var activeRequestId: Long = NO_REQUEST

    @Volatile
    private var initializationConfig: InitializationConfig? = null

    @Volatile
    private var initializedBinder: IBinder? = null

    @Volatile
    private var activeCompletion: CompletableDeferred<String>? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val connected = IGemmaRuntime.Stub.asInterface(service)
            runtime = connected
            binding?.complete(connected)
            binding = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            runtime = null
            initializedBinder = null
            binding?.completeExceptionally(GemmaProcessException("Gemma process disconnected."))
            binding = null
            activeCompletion?.completeExceptionally(
                GemmaProcessException("Gemma process disconnected."),
            )
        }

        override fun onBindingDied(name: ComponentName?) {
            onServiceDisconnected(name)
        }

        override fun onNullBinding(name: ComponentName?) {
            runtime = null
            binding?.completeExceptionally(GemmaProcessException("Gemma runtime is unavailable."))
            binding = null
        }
    }

    suspend fun initialize(
        modelPath: String,
        backend: String,
        modalityFlags: Int,
    ): String = operationMutex.withLock {
        val config = InitializationConfig(modelPath, backend, modalityFlags)
        initializationConfig = config
        val service = connect()
        initializeLocked(service, config)
    }

    private suspend fun initializeLocked(
        service: IGemmaRuntime,
        config: InitializationConfig,
    ): String {
        val completion = CompletableDeferred<String>()
        activeCompletion = completion
        val callback = object : IGemmaRuntimeCallback.Stub() {
            override fun onStateChanged(state: String, detail: String) {
                if (state == STATE_READY && !completion.isCompleted) completion.complete(detail)
            }

            override fun onResult(requestId: Long, result: String) = Unit

            override fun onError(requestId: Long, message: String) {
                if (!completion.isCompleted) {
                    completion.completeExceptionally(GemmaProcessException(message))
                }
            }
        }
        return try {
            service.initialize(
                config.modelPath,
                config.backend,
                config.modalityFlags,
                callback,
            )
            withTimeout(INITIALIZE_TIMEOUT_MS) { completion.await() }.also {
                initializedBinder = service.asBinder()
            }
        } finally {
            if (activeCompletion === completion) activeCompletion = null
        }
    }

    suspend fun generate(
        prompt: String,
        mediaPath: String = "",
        mediaType: GemmaMediaType = GemmaMediaType.TEXT,
    ): String = operationMutex.withLock {
        val service = connect()
        val binder = service.asBinder()
        if (initializedBinder !== binder || !binder.isBinderAlive) {
            val config = initializationConfig
                ?: throw GemmaProcessException("Gemma runtime has not been initialized.")
            initializeLocked(service, config)
        }
        val requestId = nextRequestId.getAndIncrement()
        activeRequestId = requestId
        val completion = CompletableDeferred<String>()
        activeCompletion = completion
        val callback = object : IGemmaRuntimeCallback.Stub() {
            override fun onStateChanged(state: String, detail: String) = Unit

            override fun onResult(callbackRequestId: Long, result: String) {
                if (callbackRequestId == requestId && !completion.isCompleted) {
                    completion.complete(result)
                }
            }

            override fun onError(callbackRequestId: Long, message: String) {
                if (callbackRequestId == requestId && !completion.isCompleted) {
                    completion.completeExceptionally(GemmaProcessException(message))
                }
            }
        }
        try {
            service.generate(requestId, prompt, mediaPath, mediaType.runtimeValue, callback)
            withTimeout(GENERATE_TIMEOUT_MS) { completion.await() }
        } finally {
            if (activeRequestId == requestId) activeRequestId = NO_REQUEST
            if (activeCompletion === completion) activeCompletion = null
        }
    }

    fun cancelActive() {
        val requestId = activeRequestId
        if (requestId == NO_REQUEST) return
        runCatching { runtime?.cancel(requestId) }
    }

    suspend fun close() {
        operationMutex.withLock {
            cancelActive()
            initializationConfig = null
            initializedBinder = null
            runCatching { runtime?.closeEngine() }
            disconnect()
        }
    }

    private suspend fun connect(): IGemmaRuntime {
        runtime?.takeIf { it.asBinder().isBinderAlive }?.let { return it }
        runtime = null
        initializedBinder = null
        if (!AppVariantConfig.hasGemma) {
            throw GemmaProcessException("Gemma is not included in this app edition.")
        }
        return connectionMutex.withLock {
            runtime?.takeIf { it.asBinder().isBinderAlive }?.let { return@withLock it }
            runtime = null
            initializedBinder = null
            val pending = binding ?: CompletableDeferred<IGemmaRuntime>().also { binding = it }
            if (!pending.isCompleted && binding === pending) {
                val intent = Intent().apply {
                    component = ComponentName(
                        context.packageName,
                        GEMMA_SERVICE_CLASS_NAME,
                    )
                }
                val didBind = withContext(Dispatchers.Main.immediate) {
                    context.bindService(
                        intent,
                        serviceConnection,
                        Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT,
                    )
                }
                if (!didBind) {
                    binding = null
                    pending.completeExceptionally(
                        GemmaProcessException("Could not bind to the Gemma runtime."),
                    )
                }
            }
            withTimeout(BIND_TIMEOUT_MS) { pending.await() }
        }
    }

    private suspend fun disconnect() {
        if (runtime == null && binding == null) return
        withContext(Dispatchers.Main.immediate) {
            runCatching { context.unbindService(serviceConnection) }
        }
        runtime = null
        binding = null
    }

    companion object {
        private const val GEMMA_SERVICE_CLASS_NAME =
            "com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaRuntimeService"
        private const val STATE_READY = "READY"
        private const val NO_REQUEST = -1L
        private const val BIND_TIMEOUT_MS = 10_000L
        private const val INITIALIZE_TIMEOUT_MS = 120_000L
        private const val GENERATE_TIMEOUT_MS = 120_000L
    }
}

enum class GemmaMediaType(val runtimeValue: Int) {
    TEXT(0),
    IMAGE(1),
    AUDIO(2),
}

class GemmaProcessException(message: String) : IllegalStateException(message)
