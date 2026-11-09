package com.kazumaproject.markdownhelperkeyboard.ai.rerank

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class CandidateScorerClient(private val context: Context) {
    private val operations = Mutex()
    private var binding: CompletableDeferred<ICandidateScorer>? = null
    private var bound = false
    private var active: CompletableDeferred<FloatArray>? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            binding?.complete(ICandidateScorer.Stub.asInterface(binder))
        }

        override fun onServiceDisconnected(name: ComponentName) = close()
        override fun onBindingDied(name: ComponentName) = close()
        override fun onNullBinding(name: ComponentName) = close()
    }

    suspend fun score(
        modelPath: String,
        tokenizerPath: String,
        prefix: String,
        candidates: Array<String>,
    ): FloatArray = withContext(Dispatchers.Main.immediate) {
        operations.withLock {
            val service = connect()
            val result = CompletableDeferred<FloatArray>()
            active = result
            val id = nextRequest.getAndIncrement()
            val callback = object : ICandidateScoreCallback.Stub() {
                override fun completed(scores: FloatArray) {
                    result.complete(scores)
                }

                override fun failed() {
                    result.completeExceptionally(IllegalStateException("Candidate scoring failed."))
                }
            }
            try {
                service.score(id, modelPath, tokenizerPath, prefix, candidates, callback)
                result.await()
            } finally {
                active = null
                if (!result.isCompleted || result.isCancelled) runCatching { service.cancel(id) }
            }
        }
    }

    private suspend fun connect(): ICandidateScorer {
        binding?.let { return it.await() }
        val connecting = CompletableDeferred<ICandidateScorer>()
        binding = connecting
        val intent = Intent().setClassName(context.packageName, SERVICE)
        if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            binding = null
            error("Candidate scoring is unavailable.")
        }
        bound = true
        return connecting.await()
    }

    fun close() {
        binding?.cancel()
        binding = null
        active?.cancel()
        active = null
        if (bound) {
            bound = false
            runCatching { context.unbindService(connection) }
        }
    }

    companion object {
        private val nextRequest = AtomicLong(1)
        private const val SERVICE = "com.kazumaproject.markdownhelperkeyboard.ai.rerank.CandidateScorerService"
    }
}
