package com.kazumaproject.markdownhelperkeyboard.zenz.runtime

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.kazumaproject.zenz.ZenzEngine
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns every llama.cpp object in the private app process named `:zenz`.
 *
 * Model work is serialized on one actor. Binder cancellation deliberately bypasses the actor and
 * only updates ZenzEngine's atomic abort generation, so a stale decode can stop even while the
 * actor thread is inside native code.
 */
class ZenzRuntimeService : Service() {
    private val actorExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ZenzRuntimeActor")
    }
    private val actorDispatcher: CoroutineDispatcher = actorExecutor.asCoroutineDispatcher()
    private val actorScope = CoroutineScope(SupervisorJob() + actorDispatcher)
    private val latestRequestId = AtomicLong(NO_REQUEST)
    private val activeRequestId = AtomicLong(NO_REQUEST)

    @Volatile
    private var initialized = false

    @Volatile
    private var initializedModelPath: String? = null

    private val binder = object : IZenzRuntime.Stub() {
        override fun initialize(
            requestId: Long,
            modelPath: String,
            nCtx: Int,
            nThreads: Int,
            callback: IZenzRuntimeCallback,
        ) {
            submit(requestId, callback) {
                if (!initialized || initializedModelPath != modelPath) {
                    initialized = false
                    initializedModelPath = null
                    val loaded = ZenzEngine.initModel(modelPath)
                    if (!loaded) {
                        callback.safeError(requestId, "Could not load the Zenz model.")
                        return@submit
                    }
                    initializedModelPath = modelPath
                }
                ZenzEngine.setRuntimeConfig(nCtx, nThreads)
                initialized = true
                callback.safeReady(requestId, android.os.Process.myPid())
            }
        }

        override fun generate(
            requestId: Long,
            profile: String,
            topic: String,
            style: String,
            preference: String,
            leftContext: String,
            rightContext: String,
            input: String,
            maxTokens: Int,
            callback: IZenzRuntimeCallback,
        ) {
            submitInitialized(requestId, callback) {
                val result = ZenzEngine.generateWithContextAndConditionsV32(
                    profile,
                    topic,
                    style,
                    preference,
                    leftContext,
                    rightContext,
                    input,
                    maxTokens,
                )
                if (isLatest(requestId)) callback.safeStringResult(requestId, result)
            }
        }

        override fun evaluate(
            requestId: Long,
            profile: String,
            topic: String,
            style: String,
            preference: String,
            leftContext: String,
            rightContext: String,
            input: String,
            candidate: String,
            callback: IZenzRuntimeCallback,
        ) {
            submitInitialized(requestId, callback) {
                val result = ZenzEngine.candidateEvaluateV32(
                    profile,
                    topic,
                    style,
                    preference,
                    leftContext,
                    rightContext,
                    input,
                    candidate,
                )
                if (isLatest(requestId)) callback.safeStringResult(requestId, result)
            }
        }

        override fun score(
            requestId: Long,
            profile: String,
            topic: String,
            style: String,
            preference: String,
            leftContext: String,
            rightContext: String,
            input: String,
            candidates: Array<String>,
            callback: IZenzRuntimeCallback,
        ) {
            submitInitialized(requestId, callback) {
                val scores = ZenzEngine.scoreCandidatesV32(
                    profile,
                    topic,
                    style,
                    preference,
                    leftContext,
                    rightContext,
                    input,
                    candidates,
                )
                if (isLatest(requestId)) callback.safeScoresResult(requestId, scores)
            }
        }

        override fun cancel(requestId: Long) {
            val cancelledQueuedRequest = latestRequestId.compareAndSet(requestId, NO_REQUEST)
            if (cancelledQueuedRequest || activeRequestId.get() == requestId) {
                ZenzEngine.cancelCurrent()
            }
        }

        override fun closeEngine() {
            latestRequestId.set(NO_REQUEST)
            ZenzEngine.cancelCurrent()
            actorScope.launch {
                closeNativeRuntime()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        latestRequestId.set(NO_REQUEST)
        ZenzEngine.cancelCurrent()
        closeNativeRuntime()
        actorScope.cancel()
        actorDispatcher.closeIfPossible()
        actorExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun submitInitialized(
        requestId: Long,
        callback: IZenzRuntimeCallback,
        operation: () -> Unit,
    ) {
        submit(requestId, callback) {
            if (!initialized) {
                callback.safeError(requestId, "Zenz model is not initialized.")
                return@submit
            }
            operation()
        }
    }

    private fun submit(
        requestId: Long,
        callback: IZenzRuntimeCallback,
        operation: () -> Unit,
    ) {
        latestRequestId.set(requestId)
        ZenzEngine.cancelCurrent()
        actorScope.launch {
            if (!isLatest(requestId)) return@launch
            activeRequestId.set(requestId)
            try {
                if (isLatest(requestId)) operation()
            } catch (error: Throwable) {
                Timber.e(error, "Zenz runtime request failed: %s", requestId)
                callback.safeError(
                    requestId,
                    error.message?.takeIf { it.isNotBlank() }
                        ?: error.javaClass.simpleName
                        ?: "Unknown Zenz runtime error.",
                )
            } finally {
                activeRequestId.compareAndSet(requestId, NO_REQUEST)
                latestRequestId.compareAndSet(requestId, NO_REQUEST)
            }
        }
    }

    private fun isLatest(requestId: Long): Boolean = latestRequestId.get() == requestId

    private fun closeNativeRuntime() {
        runCatching { ZenzEngine.closeModel() }
            .onFailure { Timber.w(it, "Failed to close Zenz native runtime") }
        initialized = false
        initializedModelPath = null
        activeRequestId.set(NO_REQUEST)
    }

    private fun IZenzRuntimeCallback.safeReady(requestId: Long, processId: Int) {
        runCatching { onReady(requestId, processId) }
    }

    private fun IZenzRuntimeCallback.safeStringResult(requestId: Long, result: String) {
        runCatching { onStringResult(requestId, result) }
    }

    private fun IZenzRuntimeCallback.safeScoresResult(requestId: Long, scores: FloatArray) {
        runCatching { onScoresResult(requestId, scores) }
    }

    private fun IZenzRuntimeCallback.safeError(requestId: Long, message: String) {
        if (isLatest(requestId)) {
            runCatching { onError(requestId, message) }
        }
    }

    private fun CoroutineDispatcher.closeIfPossible() {
        (this as? AutoCloseable)?.close()
    }

    companion object {
        private const val NO_REQUEST = -1L
    }
}
