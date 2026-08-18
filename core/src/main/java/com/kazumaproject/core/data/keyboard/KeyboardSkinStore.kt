package com.kazumaproject.core.data.keyboard

import android.content.Context
import androidx.core.util.AtomicFile
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Offline, app-private storage for validated imported skin JSON. */
class KeyboardSkinStore(
    private val directory: File,
    private val revisionContext: Context? = null,
) {
    init {
        require(directory.isAbsolute) { "Keyboard skin directory must be absolute" }
    }

    fun list(): List<StoredImportedKeyboardSkin> {
        val files = directory.listFiles { file -> file.isFile && file.extension == "json" }
            ?: return emptyList()
        return files.mapNotNull { file ->
            val definition = runCatching {
                KeyboardSkinJsonParser.parse(file.readBytes()).successOrNull()
            }.getOrNull() ?: return@mapNotNull null
            if (definition.id != file.nameWithoutExtension) return@mapNotNull null
            StoredImportedKeyboardSkin(definition, file)
        }.sortedWith(compareBy<StoredImportedKeyboardSkin> { it.definition.name.lowercase() }.thenBy { it.definition.id })
    }

    @Synchronized
    fun save(definition: ImportedKeyboardSkinDefinition, replace: Boolean): StoreWriteResult {
        val file = fileFor(definition.id)
        if (file.exists() && !replace) return StoreWriteResult.Duplicate(definition.id)
        directory.mkdirs()
        val atomicFile = AtomicFile(file)
        return try {
            val output = atomicFile.startWrite()
            try {
                output.write(definition.normalizedJson.toByteArray(StandardCharsets.UTF_8))
                output.flush()
                atomicFile.finishWrite(output)
            } catch (error: Throwable) {
                atomicFile.failWrite(output)
                throw error
            }
            revisionContext?.let(::incrementRevision)
            StoreWriteResult.Saved(file)
        } catch (error: IOException) {
            StoreWriteResult.Failure(error)
        }
    }

    @Synchronized
    fun delete(id: String): Boolean {
        if (!KeyboardSkinIdPattern.matches(id)) return false
        val file = fileFor(id)
        val atomicFile = AtomicFile(file)
        val existed = file.exists()
        atomicFile.delete()
        if (existed) revisionContext?.let(::incrementRevision)
        return existed
    }

    fun fileFor(id: String): File {
        require(KeyboardSkinIdPattern.matches(id)) { "Invalid imported skin id" }
        return File(directory, "$id.json")
    }

    companion object {
        const val DIRECTORY_NAME = "keyboard_skins/v1"
        const val REVISION_PREF_KEY = "keyboard_skin_revision"

        fun fromContext(context: Context): KeyboardSkinStore =
            KeyboardSkinStore(File(context.filesDir, DIRECTORY_NAME), context.applicationContext)

        fun revision(context: Context): Long = preferences(context).getLong(REVISION_PREF_KEY, 0L)

        @Synchronized
        fun incrementRevision(context: Context): Long {
            val next = revision(context) + 1L
            check(preferences(context).edit().putLong(REVISION_PREF_KEY, next).commit())
            return next
        }

        private fun preferences(context: Context) =
            context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
    }
}

sealed interface StoreWriteResult {
    data class Saved(val file: File) : StoreWriteResult
    data class Duplicate(val id: String) : StoreWriteResult
    data class Failure(val error: Throwable) : StoreWriteResult
}

/** In-memory compiled definitions. Drawables only read this map and never touch disk or JSON. */
object KeyboardSkinRuntime {
    private val definitions = AtomicReference<Map<String, ImportedKeyboardSkinDefinition>>(emptyMap())
    private val generationCounter = AtomicLong(0L)

    fun replace(definitions: Collection<ImportedKeyboardSkinDefinition>) {
        this.definitions.set(definitions.associateBy { it.id })
        generationCounter.incrementAndGet()
    }

    fun clear() {
        definitions.set(emptyMap())
        generationCounter.incrementAndGet()
    }

    /** Changes whenever the immutable imported-definition snapshot is replaced. */
    fun generation(): Long = generationCounter.get()

    fun definitionFor(id: String): ImportedKeyboardSkinDefinition? = definitions.get()[id]

    fun specFor(id: String): KeyboardSkinSpec? = definitionFor(id)?.spec

    fun all(): List<ImportedKeyboardSkinDefinition> = definitions.get().values
        .sortedWith(compareBy<ImportedKeyboardSkinDefinition> { it.name.lowercase() }.thenBy { it.id })

    /** Must be called from an IO dispatcher. */
    fun reloadFromDisk(context: Context): List<StoredImportedKeyboardSkin> {
        val stored = KeyboardSkinStore.fromContext(context).list()
        replace(stored.map { it.definition })
        return stored
    }
}

private val KeyboardSkinIdPattern = Regex("[a-z][a-z0-9._-]{2,63}")

private fun KeyboardSkinParseResult.successOrNull(): ImportedKeyboardSkinDefinition? =
    (this as? KeyboardSkinParseResult.Success)?.definition
