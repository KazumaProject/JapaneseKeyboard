package com.kazumaproject.markdownhelperkeyboard.zenz.runtime

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

data class ZenzRuntimeConfig(
    val modelPath: String,
    val nCtx: Int,
    val nThreads: Int,
)

class ZenzProcessException(message: String) : IllegalStateException(message)

/**
 * Main-process facade for the Zenz native runtime hosted by [ZenzRuntimeService].
 *
 * Only one model operation is submitted at a time. Cancellation remains asynchronous:
 * cancelling the awaiting coroutine sends a Binder cancellation immediately, which lets the
 * remote native abort callback stop the current llama.cpp decode.
 */
@Singleton
class ZenzRuntimeClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private sealed interface RuntimeResult {
        data class Ready(val processId: Int) : RuntimeResult
        data class Text(val value: String) : RuntimeResult
        data class Scores(val values: FloatArray) : RuntimeResult
    }

    private val connectionMutex = Mutex()
    private val operationMutex = Mutex()
    private val nextRequestId = AtomicLong(1L)
    private val stateLock = Any()

    @Volatile
    private var runtime: IZenzRuntime? = null

    @Volatile
    private var binding: CompletableDeferred<IZenzRuntime>? = null

    @Volatile
    private var serviceBound = false

    @Volatile
    private var initializedBinder: IBinder? = null

    @Volatile
    private var initializedConfig: ZenzRuntimeConfig? = null

    @Volatile
    private var activeRequestId: Long = NO_REQUEST

    @Volatile
    private var activeCompletion: CompletableDeferred<RuntimeResult>? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val connected = IZenzRuntime.Stub.asInterface(service)
            runtime = connected
            binding?.complete(connected)
            binding = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            handleRuntimeDisconnected("Zenz process disconnected.")
        }

        override fun onBindingDied(name: ComponentName?) {
            handleRuntimeDisconnected("Zenz process binding died.")
        }

        override fun onNullBinding(name: ComponentName?) {
            handleRuntimeDisconnected("Zenz runtime is unavailable.")
        }
    }

    suspend fun generate(
        config: ZenzRuntimeConfig,
        profile: String,
        topic: String,
        style: String,
        preference: String,
        leftContext: String,
        rightContext: String,
        input: String,
        maxTokens: Int,
    ): String = operationMutex.withLock {
        val service = connect()
        ensureInitializedLocked(service, config)
        val result = executeLocked(service, GENERATE_TIMEOUT_MS) { requestId, callback ->
            service.generate(
                requestId,
                profile,
                topic,
                style,
                preference,
                leftContext,
                rightContext,
                input,
                maxTokens,
                callback,
            )
        }
        (result as? RuntimeResult.Text)?.value
            ?: throw ZenzProcessException("Zenz returned an unexpected generate response.")
    }

    suspend fun evaluate(
        config: ZenzRuntimeConfig,
        profile: String?,
        topic: String?,
        style: String?,
        preference: String?,
        leftContext: String?,
        rightContext: String?,
        input: String,
        candidate: String,
    ): String = operationMutex.withLock {
        val service = connect()
        ensureInitializedLocked(service, config)
        val result = executeLocked(service, GENERATE_TIMEOUT_MS) { requestId, callback ->
            service.evaluate(
                requestId,
                profile.orEmpty(),
                topic.orEmpty(),
                style.orEmpty(),
                preference.orEmpty(),
                leftContext.orEmpty(),
                rightContext.orEmpty(),
                input,
                candidate,
                callback,
            )
        }
        (result as? RuntimeResult.Text)?.value
            ?: throw ZenzProcessException("Zenz returned an unexpected evaluate response.")
    }

    suspend fun score(
        config: ZenzRuntimeConfig,
        profile: String?,
        topic: String?,
        style: String?,
        preference: String?,
        leftContext: String?,
        rightContext: String?,
        input: String?,
        candidates: Array<String>,
    ): FloatArray = operationMutex.withLock {
        val service = connect()
        ensureInitializedLocked(service, config)
        val result = executeLocked(service, GENERATE_TIMEOUT_MS) { requestId, callback ->
            service.score(
                requestId,
                profile.orEmpty(),
                topic.orEmpty(),
                style.orEmpty(),
                preference.orEmpty(),
                leftContext.orEmpty(),
                rightContext.orEmpty(),
                input.orEmpty(),
                candidates,
                callback,
            )
        }
        (result as? RuntimeResult.Scores)?.values
            ?: throw ZenzProcessException("Zenz returned an unexpected score response.")
    }

    fun cancelActive() {
        val requestId = activeRequestId
        if (requestId == NO_REQUEST) return
        runCatching { runtime?.cancel(requestId) }
    }

    /**
     * Called from IMEService.onDestroy on the main thread. Binder work is one-way, so this does
     * not wait for native teardown. ZenzRuntimeService.onDestroy performs a final synchronous
     * close if Android tears the remote service down immediately.
     */
    fun close() {
        cancelActive()
        runCatching { runtime?.closeEngine() }
        val shouldUnbind = synchronized(stateLock) {
            val wasBound = serviceBound
            runtime = null
            binding = null
            serviceBound = false
            initializedBinder = null
            initializedConfig = null
            activeRequestId = NO_REQUEST
            activeCompletion?.cancel()
            activeCompletion = null
            wasBound
        }
        if (shouldUnbind) {
            runCatching { context.unbindService(serviceConnection) }
        }
    }

    private suspend fun ensureInitializedLocked(
        service: IZenzRuntime,
        config: ZenzRuntimeConfig,
    ) {
        val binder = service.asBinder()
        if (
            binder.isBinderAlive &&
            initializedBinder === binder &&
            initializedConfig == config
        ) {
            return
        }

        val result = executeLocked(service, INITIALIZE_TIMEOUT_MS) { requestId, callback ->
            service.initialize(
                requestId,
                config.modelPath,
                config.nCtx,
                config.nThreads,
                callback,
            )
        }
        if (result !is RuntimeResult.Ready) {
            throw ZenzProcessException("Zenz returned an unexpected initialization response.")
        }
        initializedBinder = binder
        initializedConfig = config
    }

    private suspend fun executeLocked(
        service: IZenzRuntime,
        timeoutMillis: Long,
        start: (Long, IZenzRuntimeCallback) -> Unit,
    ): RuntimeResult {
        val requestId = nextRequestId.getAndIncrement()
        val completion = CompletableDeferred<RuntimeResult>()
        activeRequestId = requestId
        activeCompletion = completion

        val callback = object : IZenzRuntimeCallback.Stub() {
            override fun onReady(callbackRequestId: Long, processId: Int) {
                if (callbackRequestId == requestId && !completion.isCompleted) {
                    completion.complete(RuntimeResult.Ready(processId))
                }
            }

            override fun onStringResult(callbackRequestId: Long, result: String) {
                if (callbackRequestId == requestId && !completion.isCompleted) {
                    completion.complete(RuntimeResult.Text(result))
                }
            }

            override fun onScoresResult(callbackRequestId: Long, scores: FloatArray) {
                if (callbackRequestId == requestId && !completion.isCompleted) {
                    completion.complete(RuntimeResult.Scores(scores))
                }
            }

            override fun onError(callbackRequestId: Long, message: String) {
                if (callbackRequestId == requestId && !completion.isCompleted) {
                    completion.completeExceptionally(ZenzProcessException(message))
                }
            }
        }

        return try {
            start(requestId, callback)
            withTimeout(timeoutMillis) { completion.await() }
        } finally {
            if (!completion.isCompleted) {
                runCatching { service.cancel(requestId) }
            }
            if (activeRequestId == requestId) activeRequestId = NO_REQUEST
            if (activeCompletion === completion) activeCompletion = null
        }
    }

    private suspend fun connect(): IZenzRuntime {
        runtime?.takeIf { it.asBinder().isBinderAlive }?.let { return it }
        runtime = null
        initializedBinder = null
        initializedConfig = null

        if (!AppVariantConfig.hasZenz) {
            throw ZenzProcessException("Zenz is not included in this app edition.")
        }

        return connectionMutex.withLock {
            runtime?.takeIf { it.asBinder().isBinderAlive }?.let { return@withLock it }
            runtime = null
            initializedBinder = null
            initializedConfig = null

            val existing = binding
            if (existing != null) {
                return@withLock withTimeout(BIND_TIMEOUT_MS) { existing.await() }
            }

            val pending = CompletableDeferred<IZenzRuntime>()
            binding = pending
            val intent = Intent().apply {
                component = ComponentName(context.packageName, ZENZ_SERVICE_CLASS_NAME)
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
                    ZenzProcessException("Could not bind to the Zenz runtime."),
                )
            } else {
                serviceBound = true
            }
            try {
                withTimeout(BIND_TIMEOUT_MS) { pending.await() }
            } catch (error: Throwable) {
                if (binding === pending) binding = null
                val shouldUnbind = serviceBound && runtime == null
                if (shouldUnbind) {
                    serviceBound = false
                    withContext(Dispatchers.Main.immediate) {
                        runCatching { context.unbindService(serviceConnection) }
                    }
                }
                throw error
            }
        }
    }

    private fun handleRuntimeDisconnected(message: String) {
        val error = ZenzProcessException(message)
        val shouldUnbind = synchronized(stateLock) {
            runtime = null
            initializedBinder = null
            initializedConfig = null
            binding?.completeExceptionally(error)
            binding = null
            activeCompletion?.completeExceptionally(error)
            val wasBound = serviceBound
            serviceBound = false
            wasBound
        }
        if (shouldUnbind) {
            runCatching { context.unbindService(serviceConnection) }
        }
    }

    companion object {
        private const val ZENZ_SERVICE_CLASS_NAME =
            "com.kazumaproject.markdownhelperkeyboard.zenz.runtime.ZenzRuntimeService"
        private const val NO_REQUEST = -1L
        private const val BIND_TIMEOUT_MS = 10_000L
        private const val INITIALIZE_TIMEOUT_MS = 30_000L
        private const val GENERATE_TIMEOUT_MS = 30_000L
    }
}
