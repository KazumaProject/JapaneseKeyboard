package com.kazumaproject.markdownhelperkeyboard.gemma

import android.content.SharedPreferences
import java.io.File
import java.security.MessageDigest

/** Stored with existing settings backups; model weights themselves are never backed up here. */
internal class AiModelProfiles(private val preferences: SharedPreferences) {
    fun projectorPath(modelPath: String): String = preferences.getString(key(modelPath), "").orEmpty()

    fun setProjectorPath(modelPath: String, projectorPath: String) {
        preferences.edit().apply {
            if (projectorPath.isBlank()) remove(key(modelPath)) else putString(key(modelPath), projectorPath)
        }.apply()
    }

    fun modelName(path: String): String = preferences.getString("ai_model_name_" + suffix(path), "").orEmpty()

    fun setModelName(path: String, name: String) {
        preferences.edit().putString("ai_model_name_" + suffix(path), name).apply()
    }

    private fun key(path: String): String = "ai_model_projector_" + suffix(path)
    private fun suffix(path: String): String = MessageDigest.getInstance("SHA-256")
        .digest(File(path).absolutePath.toByteArray()).joinToString("") { "%02x".format(it) }
}

enum class AiModelFormat(val extension: String) {
    LITERT_LM("litertlm"), GGUF("gguf");

    companion object {
        fun fromFilename(filename: String): AiModelFormat? = entries.firstOrNull {
            filename.substringBefore('?').substringAfterLast('.', "").equals(it.extension, true)
        }
    }
}
