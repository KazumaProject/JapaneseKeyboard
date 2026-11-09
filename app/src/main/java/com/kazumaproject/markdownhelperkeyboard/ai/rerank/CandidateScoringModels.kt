package com.kazumaproject.markdownhelperkeyboard.ai.rerank

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class CandidateScoringModels(private val context: Context) {
    data class Selection(val model: String, val tokenizer: String)

    private val prefs = context.getSharedPreferences("candidate_scoring_models", Context.MODE_PRIVATE)
    private val directory = File(context.noBackupFilesDir, "candidate-scorer")

    fun selection(): Selection? {
        val model = prefs.getString(MODEL, "").orEmpty()
        if (model.isEmpty()) return null
        val tokenizer = if (model.endsWith(".onnx")) prefs.getString(TOKENIZER, "").orEmpty() else ""
        if (model.endsWith(".onnx") && tokenizer.isEmpty()) return null
        return Selection(model, tokenizer)
    }

    fun label(tokenizer: Boolean): String? =
        prefs.getString((if (tokenizer) TOKENIZER else MODEL) + "_label", null)

    suspend fun import(uri: Uri, tokenizer: Boolean) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: error("The document provider did not return a file name.")
        val format = name.substringAfterLast('.').lowercase()
        require(if (tokenizer) format == "model" else format == "gguf" || format == "onnx")

        check(directory.isDirectory || directory.mkdirs())
        val file = File.createTempFile("scorer-", ".$format", directory)
        val key = if (tokenizer) TOKENIZER else MODEL
        try {
            requireNotNull(resolver.openInputStream(uri)).use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
            }
            require(file.length() > 0)
            if (format == "gguf") {
                val magic = ByteArray(4)
                file.inputStream().use { check(it.read(magic) == magic.size) }
                require(magic.contentEquals("GGUF".encodeToByteArray()))
            }
            ensureActive()
            val old = prefs.getString(key, "").orEmpty()
            check(prefs.edit().putString(key, file.absolutePath).putString(key + "_label", name).commit())
            if (old.isNotEmpty()) deleteManagedFile(old)
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    fun clear() {
        val files = listOf(MODEL, TOKENIZER).mapNotNull { prefs.getString(it, null) }
        prefs.edit().clear().apply()
        files.forEach(::deleteManagedFile)
    }

    private fun deleteManagedFile(path: String) {
        val file = File(path)
        if (file.parentFile == directory) file.delete()
    }

    companion object {
        private const val MODEL = "model"
        private const val TOKENIZER = "tokenizer"
    }
}
