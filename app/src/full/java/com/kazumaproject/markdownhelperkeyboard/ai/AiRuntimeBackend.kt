package com.kazumaproject.markdownhelperkeyboard.ai

import com.kazumaproject.ai.GgufEngine
import org.json.JSONObject
import timber.log.Timber

internal data class AiModelInfo(val name: String, val flags: Int, val projector: Boolean = false) {
    fun encode(): String = JSONObject().put("name", name).put("flags", flags)
        .put("projector", projector).toString()

    companion object {
        fun decode(value: String): AiModelInfo = JSONObject(value).let {
            AiModelInfo(it.optString("name"), it.optInt("flags"), it.optBoolean("projector"))
        }
    }
}

/** All methods except cancel run on the service actor, outside the IME process. */
internal interface AiRuntimeBackend : AutoCloseable {
    val modalityFlags: Int
    fun inspect(path: String): AiModelInfo
    fun initialize(path: String, backend: String, projectorPath: String): String
    fun resetCancellation()
    fun generate(prompt: String, mediaPath: String, mediaType: Int, instruction: String = "", targetLanguage: String = ""): String
    fun cancel()
}

internal class GgufRuntimeBackend : AiRuntimeBackend {
    @Volatile private var handle = 0L
    override var modalityFlags = 0
        private set

    override fun inspect(path: String) = AiModelInfo.decode(GgufEngine.inspect(path))

    override fun initialize(path: String, backend: String, projectorPath: String): String {
        close()
        require(!inspect(path).projector) { "Select a language model, then attach its projector separately." }
        if (projectorPath.isNotBlank()) require(inspect(projectorPath).projector) {
            "The selected file is not a multimodal projector."
        }
        val resolved = if (backend == "gpu_if_available") {
            try {
                handle = GgufEngine.initialize(path, projectorPath, Runtime.getRuntime().availableProcessors(), true)
                "GPU"
            } catch (error: Exception) {
                Timber.w(error, "GGUF GPU initialization failed; retrying with CPU")
                handle = GgufEngine.initialize(path, projectorPath, Runtime.getRuntime().availableProcessors(), false)
                "CPU_FALLBACK"
            }
        } else {
            handle = GgufEngine.initialize(path, projectorPath, Runtime.getRuntime().availableProcessors(), false)
            if (backend == "npu_if_available") "NPU_CPU_FALLBACK" else "CPU"
        }
        modalityFlags = GgufEngine.capabilities(handle)
        return resolved
    }

    override fun resetCancellation() = GgufEngine.resetCancellation(handle)
    override fun generate(prompt: String, mediaPath: String, mediaType: Int, instruction: String, targetLanguage: String): String =
        GgufEngine.generate(handle, prompt, mediaPath, mediaType, instruction, targetLanguage)
    override fun cancel() = GgufEngine.cancel(handle)
    override fun close() {
        val previous = handle
        handle = 0L
        if (previous != 0L) GgufEngine.close(previous)
        modalityFlags = 0
    }
}
