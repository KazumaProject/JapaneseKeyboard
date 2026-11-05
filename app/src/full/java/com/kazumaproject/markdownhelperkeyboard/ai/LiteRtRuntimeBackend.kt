package com.kazumaproject.markdownhelperkeyboard.ai

import java.lang.reflect.Array as ReflectArray
import java.util.concurrent.atomic.AtomicBoolean

/** Runtime-only dependency keeps the upstream Kotlin compiler version out of app compilation. */
internal class LiteRtRuntimeBackend(private val cachePath: String) : AiRuntimeBackend {
    private var engine: Any? = null
    @Volatile private var conversation: Any? = null
    private val cancelled = AtomicBoolean(false)
    override var modalityFlags = 0
        private set

    override fun inspect(path: String): AiModelInfo {
        val clazz = Class.forName(PREFIX + "ModelInfo")
        val companion = clazz.getField("Companion").get(null)
        val info = requireNotNull(companion.javaClass.getMethod("from", String::class.java).invoke(companion, path))
        return try {
            require(info.call("getModelType").toString() != "EMBEDDING") { "Embedding models cannot generate keyboard text." }
            val modalities = requireNotNull(info.call("inputModalities"))
            val flags = (if (modalities.call("getVision") == true) 1 else 0) or
                (if (modalities.call("getAudio") == true) 2 else 0)
            AiModelInfo("", flags)
        } finally { info.call("close") }
    }

    override fun initialize(path: String, backend: String, projectorPath: String): String {
        close()
        require(projectorPath.isBlank()) { "LiteRT-LM models contain their own media components." }
        modalityFlags = inspect(path).flags
        return try {
            engine = createEngine(path, backend)
            if (backend == "gpu_if_available") "GPU" else "CPU"
        } catch (error: Exception) {
            if (backend != "gpu_if_available") throw error
            engine = createEngine(path, "cpu")
            "CPU_FALLBACK"
        }
    }

    private fun createEngine(path: String, backend: String): Any {
        val backendClass = Class.forName(PREFIX + "Backend")
        fun newBackend(gpu: Boolean): Any = Class.forName(PREFIX + if (gpu) "Backend\$GPU" else "Backend\$CPU")
            .getConstructor().newInstance()
        val textBackend = newBackend(backend == "gpu_if_available")
        val configClass = Class.forName(PREFIX + "EngineConfig")
        val config = configClass.getConstructor(
            String::class.java, backendClass, backendClass, backendClass,
            Integer::class.java, Integer::class.java, String::class.java,
        ).newInstance(path, textBackend, if (modalityFlags and 1 != 0) textBackend else null,
            if (modalityFlags and 2 != 0) newBackend(false) else null, null,
            if (modalityFlags and 1 != 0) 8 else null, cachePath)
        val candidate = Class.forName(PREFIX + "Engine").getConstructor(configClass).newInstance(config)
        try { candidate.call("initialize") } catch (error: Exception) {
            runCatching { candidate.call("close") }
            throw error
        }
        return candidate
    }

    override fun resetCancellation() { cancelled.set(false) }

    override fun generate(prompt: String, mediaPath: String, mediaType: Int): String {
        val activeEngine = requireNotNull(engine) { "AI model is not ready." }
        val configClass = Class.forName(PREFIX + "ConversationConfig")
        val samplerClass = Class.forName(PREFIX + "SamplerConfig")
        val sampler = samplerClass.getConstructor(Int::class.javaPrimitiveType, Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType, Int::class.javaPrimitiveType).newInstance(1, 1.0, 0.0, 0)
        val config = configClass.getConstructor(Class.forName(PREFIX + "Contents"), List::class.java,
            List::class.java, samplerClass).newInstance(null, emptyList<Any>(), emptyList<Any>(), sampler)
        val activeConversation = requireNotNull(activeEngine.javaClass
            .getMethod("createConversation", configClass).invoke(activeEngine, config))
        conversation = activeConversation
        return try {
            check(!cancelled.get()) { "AI request was cancelled." }
            val contentsClass = Class.forName(PREFIX + "Contents")
            val response = requireNotNull(activeConversation.javaClass.getMethod("sendMessage", contentsClass, Map::class.java)
                .invoke(activeConversation, contents(prompt, mediaPath, mediaType), mapOf("enable_thinking" to false)))
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
    companion object { private const val PREFIX = "com.google.ai.edge.litertlm." }
}
