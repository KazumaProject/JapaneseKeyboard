package com.kazumaproject.markdownhelperkeyboard.ai.rerank

import android.app.Service
import android.content.Intent
import com.kazumaproject.ai.GgufEngine
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CandidateScorerService : Service() {
    private class Request {
        val cancelled = AtomicBoolean()
        private var cancelInference: (() -> Unit)? = null

        @Synchronized fun setCancelInference(cancel: (() -> Unit)?) {
            cancelInference = cancel
        }

        @Synchronized fun cancel() {
            cancelled.set(true)
            cancelInference?.invoke()
        }
    }

    private data class Model(val path: String, val tokenizerPath: String)
    private val worker = Executors.newSingleThreadExecutor()
    private val requests = ConcurrentHashMap<Long, Request>()
    private var model: Model? = null
    private var onnx: OnnxCandidateScorer? = null
    private var gguf: Long = 0

    private val binder = object : ICandidateScorer.Stub() {
        override fun score(
            requestId: Long,
            modelPath: String,
            tokenizerPath: String,
            prefix: String,
            candidates: Array<String>,
            callback: ICandidateScoreCallback,
        ) {
            val request = Request()
            if (requests.putIfAbsent(requestId, request) != null) return
            worker.execute {
                try {
                    if (request.cancelled.get()) return@execute
                    require(candidates.size <= 16 && candidates.all { it.isNotBlank() && it.length <= 256 })
                    require(prefix.length <= 128)
                    load(Model(modelPath, tokenizerPath))
                    val scores = when {
                        candidates.isEmpty() -> FloatArray(0)
                        onnx != null -> {
                            val scorer = checkNotNull(onnx)
                            request.setCancelInference(scorer::cancel)
                            scorer.score(prefix, candidates.asList(), request.cancelled::get)
                        }
                        else -> {
                            val handle = gguf
                            GgufEngine.resetCancellation(handle)
                            request.setCancelInference { GgufEngine.cancel(handle) }
                            if (request.cancelled.get()) return@execute
                            GgufEngine.score(handle, prefix, candidates)
                        }
                    }
                    if (!request.cancelled.get()) runCatching { callback.completed(scores) }
                } catch (_: Exception) {
                    if (!request.cancelled.get()) runCatching { callback.failed() }
                } finally {
                    request.setCancelInference(null)
                    requests.remove(requestId, request)
                }
            }
        }

        override fun cancel(requestId: Long) {
            requests[requestId]?.let { runCatching(it::cancel) }
        }
    }

    override fun onBind(intent: Intent) = binder

    private fun load(requested: Model) {
        if (model == requested) return
        closeModel()
        when (requested.path.substringAfterLast('.').lowercase()) {
            "gguf" -> {
                gguf = GgufEngine.initialize(requested.path, "", THREADS, false)
                check(gguf != 0L)
            }
            "onnx" -> onnx = OnnxCandidateScorer(requested.path, requested.tokenizerPath, THREADS)
            else -> error("Unsupported candidate scoring model.")
        }
        model = requested
    }

    private fun closeModel() {
        model = null
        onnx?.close()
        onnx = null
        if (gguf != 0L) GgufEngine.close(gguf)
        gguf = 0L
    }

    override fun onDestroy() {
        requests.values.forEach { runCatching(it::cancel) }
        worker.execute(::closeModel)
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val THREADS = 4
    }
}
