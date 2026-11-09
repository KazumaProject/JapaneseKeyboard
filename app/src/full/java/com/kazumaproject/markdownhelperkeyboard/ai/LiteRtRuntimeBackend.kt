package com.kazumaproject.markdownhelperkeyboard.ai

import android.content.Context
import android.os.Build
import com.kazumaproject.markdownhelperkeyboard.R
import java.io.File
import java.lang.reflect.Array as ReflectArray
import java.util.concurrent.atomic.AtomicBoolean

/** Runtime-only dependency keeps the upstream Kotlin compiler version out of app compilation. */
internal class LiteRtRuntimeBackend(private val context: Context) : AiRuntimeBackend {
    private var engine: Any? = null
    @Volatile private var conversation: Any? = null
    private val cancelled = AtomicBoolean(false)
    override var modalityFlags = 0
        private set

    private inline fun <T> withModelInfo(path: String, read: (Any) -> T): T {
        val clazz = Class.forName(PREFIX + "ModelInfo")
        val companion = clazz.getField("Companion").get(null)
        val info = requireNotNull(companion.javaClass.getMethod("from", String::class.java).invoke(companion, path))
        return try {
            require(info.call("getModelType").toString() != "EMBEDDING") { "Embedding models cannot generate keyboard text." }
            read(info)
        } finally { info.call("close") }
    }

    private fun modalityFlags(info: Any): Int {
        val modalities = requireNotNull(info.call("inputModalities"))
        return (if (modalities.call("getVision") == true) 1 else 0) or
            (if (modalities.call("getAudio") == true) 2 else 0)
    }

    override fun inspect(path: String) = withModelInfo(path) { AiModelInfo("", modalityFlags(it)) }

    override fun initialize(path: String, backend: String, projectorPath: String): String {
        close()
        require(projectorPath.isBlank()) { "LiteRT-LM models contain their own media components." }
        val tensorNpu = withModelInfo(path) { info ->
            modalityFlags = modalityFlags(info)
            if (backend == "npu_if_available") tensorNpuConfig(info, path) else null
        }
        if (backend == "npu_if_available") {
            engine = createEngine(path, gpu = false, tensorNpu = tensorNpu)
            return if (tensorNpu != null) "NPU" else "NPU_CPU_FALLBACK"
        }
        val gpu = backend == "gpu_if_available"
        return try {
            engine = createEngine(path, gpu)
            if (gpu) "GPU" else "CPU"
        } catch (error: Exception) {
            if (!gpu) throw error
            engine = createEngine(path, gpu = false)
            "CPU_FALLBACK"
        }
    }

    private data class TensorNpuConfig(val libraryDir: String, val vision: Boolean, val audio: Boolean)

    private fun tensorNpuConfig(info: Any, path: String): TensorNpuConfig? {
        val modalityClass = Class.forName(PREFIX + "Modality")
        fun modelInfo(method: String, modality: String): Any? {
            val value = modalityClass.getField(modality).get(null)
            return info.javaClass.getMethod(method, modalityClass).invoke(info, value)
        }
        fun backends(modality: String): List<String> =
            (modelInfo("supportedBackends", modality) as List<*>).map { it.toString() }

        val textBackends = backends("TEXT")
        if ("NPU" !in textBackends) return null

        val brand = modelInfo("npuBrand", "TEXT").toString()
        // Some published Tensor models omit the target from the container metadata.
        val filenameSoc = TENSOR_MODEL_SUFFIX.find(File(path).nameWithoutExtension)?.groupValues?.get(1)
        val targetSoc = (modelInfo("socName", "TEXT") as? String)?.takeIf { it.isNotBlank() } ?: filenameSoc
        if (brand != "GOOGLE_TENSOR" && !(brand == "UNKNOWN" && TENSOR_SOC.matches(targetSoc.orEmpty()))) {
            require("CPU" in textBackends) { context.getString(R.string.ai_npu_requires_tensor_model) }
            return null
        }

        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            Build.SOC_MANUFACTURER.equals("Google", ignoreCase = true) &&
            TENSOR_SOC.matches(Build.SOC_MODEL) &&
            Build.SOC_MODEL.equals(targetSoc, ignoreCase = true)
        ) { context.getString(R.string.ai_npu_incompatible_tensor_model) }

        val libraryDir = context.applicationInfo.nativeLibraryDir
        check(File(libraryDir, "libLiteRtDispatch_GoogleTensor.so").isFile) {
            context.getString(R.string.ai_npu_missing_dispatch)
        }
        val usesFilenameTarget = filenameSoc.equals(targetSoc, ignoreCase = true)
        return TensorNpuConfig(
            libraryDir,
            vision = modalityFlags and 1 != 0 && (usesFilenameTarget || "NPU" in backends("VISION")),
            audio = modalityFlags and 2 != 0 && (usesFilenameTarget || "NPU" in backends("AUDIO")),
        )
    }

    private fun createEngine(path: String, gpu: Boolean, tensorNpu: TensorNpuConfig? = null): Any {
        val backendClass = Class.forName(PREFIX + "Backend")
        fun newBackend(gpu: Boolean): Any = Class.forName(PREFIX + if (gpu) "Backend\$GPU" else "Backend\$CPU")
            .getConstructor().newInstance()
        val npu = tensorNpu?.let {
            Class.forName(PREFIX + "Backend\$NPU").getConstructor(String::class.java).newInstance(it.libraryDir)
        }
        val textBackend = npu ?: newBackend(gpu)
        val visionBackend = if (modalityFlags and 1 == 0) null else when {
            tensorNpu?.vision == true -> npu
            tensorNpu != null -> newBackend(false)
            else -> textBackend
        }
        val audioBackend = if (modalityFlags and 2 == 0) null
            else if (tensorNpu?.audio == true) npu else newBackend(false)
        val configClass = Class.forName(PREFIX + "EngineConfig")
        val config = configClass.getConstructor(
            String::class.java, backendClass, backendClass, backendClass,
            Integer::class.java, Integer::class.java, String::class.java,
        ).newInstance(path, textBackend, visionBackend, audioBackend, null,
            if (visionBackend != null) 8 else null, context.cacheDir.absolutePath)
        val candidate = Class.forName(PREFIX + "Engine").getConstructor(configClass).newInstance(config)
        try { candidate.call("initialize") } catch (error: Exception) {
            runCatching { candidate.call("close") }
            throw error
        }
        return candidate
    }

    override fun resetCancellation() { cancelled.set(false) }

    override fun generate(prompt: String, mediaPath: String, mediaType: Int, instruction: String, targetLanguage: String): String {
        val activeEngine = requireNotNull(engine) { "AI model is not ready." }
        val configClass = Class.forName(PREFIX + "ConversationConfig")
        val samplerClass = Class.forName(PREFIX + "SamplerConfig")
        val sampler = samplerClass.getConstructor(Int::class.javaPrimitiveType, Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType, Int::class.javaPrimitiveType).newInstance(1, 1.0, 0.0, 0)
        val config = configClass.getConstructor(Class.forName(PREFIX + "Contents"), List::class.java,
            List::class.java, samplerClass).newInstance(if (instruction.isBlank()) null else contents(instruction, "", 0), emptyList<Any>(), emptyList<Any>(), sampler)
        val activeConversation = requireNotNull(activeEngine.javaClass
            .getMethod("createConversation", configClass).invoke(activeEngine, config))
        conversation = activeConversation
        return try {
            check(!cancelled.get()) { "AI request was cancelled." }
            val contentsClass = Class.forName(PREFIX + "Contents")
            val response = requireNotNull(activeConversation.javaClass.getMethod("sendMessage", contentsClass, Map::class.java)
                .invoke(activeConversation, contents(prompt, mediaPath, mediaType), buildMap<String, Any> {
                    put("enable_thinking", false)
                    if (targetLanguage.isNotBlank()) put("target_lang", targetLanguage)
                }))
            check(!cancelled.get()) { "AI request was cancelled." }
            extractText(response)
        } finally {
            conversation = null
            activeConversation.call("close")
        }
    }

    private fun contents(prompt: String, path: String, type: Int): Any {
        val clazz = Class.forName(PREFIX + "Contents")
        val companion = clazz.getField("Companion").get(null)
        if (type == 0) return requireNotNull(companion.javaClass.getMethod("of", String::class.java).invoke(companion, prompt))
        val values = ReflectArray.newInstance(Class.forName(PREFIX + "Content"), 2)
        ReflectArray.set(values, 0, Class.forName(PREFIX + if (type == 1) "Content\$ImageFile" else "Content\$AudioFile")
            .getConstructor(String::class.java).newInstance(path))
        ReflectArray.set(values, 1, Class.forName(PREFIX + "Content\$Text").getConstructor(String::class.java).newInstance(prompt))
        return requireNotNull(companion.javaClass.getMethod("of", values.javaClass).invoke(companion, values))
    }

    internal fun extractText(message: Any): String {
        val contents = message.call("getContents") ?: return ""
        val values = contents.call("getContents") as? List<*> ?: return ""
        return values.joinToString("") { content ->
            if (content?.javaClass?.name == PREFIX + "Content\$Text") content.call("getText") as? String ?: "" else ""
        }
    }

    override fun cancel() {
        cancelled.set(true)
        runCatching { conversation?.call("cancelProcess") }
    }

    override fun close() {
        cancel()
        runCatching { engine?.call("close") }
        engine = null
        modalityFlags = 0
    }

    private fun Any.call(name: String): Any? = javaClass.getMethod(name).invoke(this)
    companion object {
        private const val PREFIX = "com.google.ai.edge.litertlm."
        private val TENSOR_SOC = Regex("Tensor_G[56]", RegexOption.IGNORE_CASE)
        private val TENSOR_MODEL_SUFFIX = Regex("_Google_(Tensor_G[56])$", RegexOption.IGNORE_CASE)
    }
}
