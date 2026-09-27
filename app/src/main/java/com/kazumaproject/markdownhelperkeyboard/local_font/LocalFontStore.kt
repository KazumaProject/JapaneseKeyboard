package com.kazumaproject.markdownhelperkeyboard.local_font

import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

internal data class LocalFontRecord(
    val id: String,
    val extension: String,
    val displayName: String,
    val sizeBytes: Long,
    val sha256: String,
)

internal data class LocalFontStoredState(val record: LocalFontRecord?)

/** Binary files and their sole durable activation record live outside Android backups. */
internal class LocalFontStore(noBackupFilesDir: File) {
    private val root = File(noBackupFilesDir, "custom_fonts").apply { mkdirs() }
    private val staging = File(root, "staging").apply { mkdirs() }
    private val stateFile = AtomicFile(File(root, "state.json"))

    init {
        require(root.canonicalFile.parentFile == noBackupFilesDir.canonicalFile)
        require(staging.canonicalFile.parentFile == root.canonicalFile)
    }

    fun newStagingFile(): File = File(staging, "${UUID.randomUUID()}.part")

    fun newRecord(displayName: String, extension: String, size: Long, sha256: String): LocalFontRecord {
        require(extension == "ttf" || extension == "otf")
        return LocalFontRecord(UUID.randomUUID().toString(), extension, displayName, size, sha256)
    }

    fun publish(staged: File, record: LocalFontRecord): File {
        require(staged.canonicalFile.parentFile == staging.canonicalFile)
        val target = fileFor(record)
        if (!staged.renameTo(target)) throw IOException("Unable to finalize local font")
        return target
    }

    fun fileFor(record: LocalFontRecord): File {
        require(record.id.matches(ID_PATTERN))
        require(record.extension == "ttf" || record.extension == "otf")
        val file = File(root, "${record.id}.${record.extension}")
        require(file.canonicalFile.parentFile == root.canonicalFile)
        return file
    }

    fun readState(): LocalFontStoredState? {
        if (!stateFile.baseFile.exists()) return null
        return try {
            val json = JSONObject(stateFile.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
            if (json.optInt("schema") != SCHEMA_VERSION) return null
            if (json.optString("mode") == "standard") return LocalFontStoredState(null)
            if (json.optString("mode") != "custom") return null
            val record = LocalFontRecord(
                id = json.getString("id"),
                extension = json.getString("extension"),
                displayName = sanitizeDisplayName(json.optString("displayName")),
                sizeBytes = json.getLong("sizeBytes"),
                sha256 = json.getString("sha256"),
            )
            fileFor(record)
            if (record.sizeBytes !in 1..MAX_LOCAL_FONT_BYTES || !record.sha256.matches(SHA_PATTERN)) return null
            LocalFontStoredState(record)
        } catch (_: Exception) {
            null
        }
    }

    fun writeState(record: LocalFontRecord?) {
        val json = if (record == null) {
            JSONObject().put("schema", SCHEMA_VERSION).put("mode", "standard")
        } else {
            fileFor(record)
            JSONObject()
                .put("schema", SCHEMA_VERSION)
                .put("mode", "custom")
                .put("id", record.id)
                .put("extension", record.extension)
                .put("displayName", record.displayName)
                .put("sizeBytes", record.sizeBytes)
                .put("sha256", record.sha256)
        }
        var stream: FileOutputStream? = null
        try {
            stream = stateFile.startWrite()
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
            stateFile.finishWrite(stream)
        } catch (e: Exception) {
            if (stream != null) stateFile.failWrite(stream)
            throw e
        }
    }

    fun delete(file: File?) {
        if (file == null) return
        if (file.canonicalFile.parentFile == root.canonicalFile || file.canonicalFile.parentFile == staging.canonicalFile) {
            file.delete()
        }
    }

    fun collectGarbage(activeId: String?, previewId: String?) {
        staging.listFiles().orEmpty().forEach(::delete)
        root.listFiles().orEmpty().forEach { file ->
            if (file == staging || file.name == "state.json") return@forEach
            if (file.extension !in setOf("ttf", "otf")) return@forEach
            if (file.nameWithoutExtension != activeId && file.nameWithoutExtension != previewId) delete(file)
        }
    }

    companion object {
        private const val SCHEMA_VERSION = 1
        private val ID_PATTERN = Regex("[0-9a-fA-F-]{36}")
        private val SHA_PATTERN = Regex("[0-9a-f]{64}")

        fun sanitizeDisplayName(value: String): String {
            val basename = value.substringAfterLast('/').substringAfterLast('\\')
            val normalized = basename.replace(Regex("[\\p{Cntrl}]"), " ")
                .replace(Regex("\\s+"), " ").trim()
            return normalized.take(120).takeUnless { it == "." || it == ".." }.orEmpty()
                .ifEmpty { "Local font" }
        }
    }
}
