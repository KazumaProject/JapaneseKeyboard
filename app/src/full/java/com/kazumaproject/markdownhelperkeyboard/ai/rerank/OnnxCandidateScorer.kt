package com.kazumaproject.markdownhelperkeyboard.ai.rerank

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.kazumaproject.ai.SentencePieceTokenizer
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.concurrent.CancellationException

internal class OnnxCandidateScorer(
    modelPath: String,
    tokenizerPath: String,
    threads: Int,
) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val tokenizer = SentencePieceTokenizer.open(tokenizerPath)
    private val session = try {
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(threads)
            options.setInterOpNumThreads(1)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            env.createSession(modelPath, options).also { loaded ->
                if (loaded.inputNames != INPUTS || "scores" !in loaded.outputNames) {
                    loaded.close()
                    error("The ONNX model does not implement the candidate scoring interface.")
                }
            }
        }
    } catch (error: Throwable) {
        SentencePieceTokenizer.close(tokenizer)
        throw error
    }
    private val lock = Any()
    private var run: OrtSession.RunOptions? = null

    fun score(prefix: String, candidates: List<String>, isCancelled: () -> Boolean): FloatArray {
        val input = SentencePieceTokenizer.encode(tokenizer, prefix, true)
        val targets = candidates.map { SentencePieceTokenizer.encode(tokenizer, it, false) }
        val longest = targets.maxOf { it.size }
        val width = input.size + longest - 1
        require(longest > 0 && width <= 256)
        val batch = candidates.size
        val ids = LongArray(batch * width) { input[0].toLong() }
        val positions = LongArray(batch * longest)
        val target = LongArray(batch * longest)
        val mask = FloatArray(batch * longest)
        for ((row, tokens) in targets.withIndex()) {
            input.forEachIndexed { index, value -> ids[row * width + index] = value.toLong() }
            for (index in tokens.indices) {
                if (index > 0) ids[row * width + input.size + index - 1] = tokens[index - 1].toLong()
                val offset = row * longest + index
                positions[offset] = (input.size + index - 1).toLong()
                target[offset] = tokens[index].toLong()
                mask[offset] = 1f
            }
        }
        val idShape = longArrayOf(batch.toLong(), width.toLong())
        val targetShape = longArrayOf(batch.toLong(), longest.toLong())
        val score = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), idShape).use { inputTensor ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(positions), targetShape).use { positionTensor ->
                OnnxTensor.createTensor(env, LongBuffer.wrap(target), targetShape).use { targetTensor ->
                    OnnxTensor.createTensor(env, FloatBuffer.wrap(mask), targetShape).use { maskTensor ->
                        infer(
                            mapOf("ids" to inputTensor, "positions" to positionTensor,
                                "target" to targetTensor, "mask" to maskTensor),
                            isCancelled,
                        )
                    }
                }
            }
        }
        require(score.size == batch)
        return score.mapIndexed { index, value -> if (targets[index].isEmpty()) Float.NaN else value }.toFloatArray()
    }

    private fun infer(inputs: Map<String, OnnxTensor>, isCancelled: () -> Boolean): FloatArray =
        OrtSession.RunOptions().use { options ->
            synchronized(lock) {
                if (isCancelled()) throw CancellationException()
                run = options
            }
            try {
                session.run(inputs, setOf("scores"), options).use { result ->
                    (result[0].value as FloatArray).copyOf()
                }
            } finally {
                synchronized(lock) { run = null }
            }
        }

    fun cancel() {
        synchronized(lock) { run?.setTerminate(true) }
    }

    override fun close() {
        try {
            session.close()
        } finally {
            SentencePieceTokenizer.close(tokenizer)
        }
    }

    companion object {
        private val INPUTS = setOf("ids", "positions", "target", "mask")
    }
}
