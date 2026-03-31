package com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.SystemUserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserTemplateRepository
import com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.SystemUserDictionaryContextIdResolver
import com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.SystemUserDictionaryExternalParser
import com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.IdDefEntry
import com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.IdDefEntryRepository
import com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.SystemUserDictionaryBuilder
import com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.SystemUserDictionaryFileManager
import com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.database.SystemUserDictionaryEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SystemUserDictionaryBuilderViewModel @Inject constructor(
    private val repository: SystemUserDictionaryRepository,
    private val learnRepository: LearnRepository,
    private val userDictionaryRepository: UserDictionaryRepository,
    private val userTemplateRepository: UserTemplateRepository,
    private val idDefEntryRepository: IdDefEntryRepository,
    private val systemUserDictionaryBuilder: SystemUserDictionaryBuilder,
    private val fileManager: SystemUserDictionaryFileManager,
    private val kanaKanjiEngine: KanaKanjiEngine,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    enum class ImportMode {
        APPEND,
        REPLACE_ALL,
    }

    enum class InternalImportSource {
        LEARN,
        USER_WORD,
        USER_TEMPLATE,
    }

    sealed class DictionaryImportResult {
        data class BuiltDictionary(val importedEntries: Int?) : DictionaryImportResult()
        data class ExternalDictionary(val importedEntries: Int, val skippedLines: Int) : DictionaryImportResult()
        data class InternalDictionary(
            val importedEntries: Int,
            val skippedEntries: Int,
            val sourceCount: Int,
        ) : DictionaryImportResult()
        object Failed : DictionaryImportResult()
    }

    private val gson = Gson()

    companion object {
        const val DEFAULT_SCORE = 4000
        const val DEFAULT_CONTEXT_ID = 1851
    }

    val allEntries: LiveData<List<SystemUserDictionaryEntry>> = repository.allEntries

    fun getIdDefEntries(): List<IdDefEntry> = idDefEntryRepository.getEntries()

    fun getIdDefEntryById(id: Int): IdDefEntry? = idDefEntryRepository.findById(id)

    suspend fun insert(entry: SystemUserDictionaryEntry) = repository.insert(entry)

    suspend fun update(entry: SystemUserDictionaryEntry) = repository.update(entry)

    suspend fun delete(id: Int) = repository.delete(id)

    suspend fun deleteAll() = repository.deleteAll()

    suspend fun buildDictionary(): SystemUserDictionaryFileManager.BuildMetadata = withContext(Dispatchers.IO) {
        val entries = repository.getAllForBuild()
        val metadata = systemUserDictionaryBuilder.build(entries)
        kanaKanjiEngine.loadSystemUserDictionaryFromFiles(context)
        metadata
    }

    suspend fun exportBuiltDictionary(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val entriesJson = gson.toJson(repository.getAllForBuild())
            val result = context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                fileManager.exportBuiltDictionary(outputStream, entriesJson)
            }
            result ?: false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun importBuiltDictionary(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        importDictionary(uri, ImportMode.REPLACE_ALL) !is DictionaryImportResult.Failed
    }

    suspend fun importDictionary(uri: Uri, importMode: ImportMode): DictionaryImportResult =
        withContext(Dispatchers.IO) {
            val contextResolver = SystemUserDictionaryContextIdResolver(
                idEntries = idDefEntryRepository.getEntries(),
                defaultContextId = DEFAULT_CONTEXT_ID,
            )
            tryImportBuiltDictionary(uri, importMode)?.let { return@withContext it }
            tryImportExternalDictionary(uri, importMode, contextResolver) ?: DictionaryImportResult.Failed
        }

    suspend fun importFromInternalSources(
        sources: Set<InternalImportSource>,
        importMode: ImportMode,
    ): DictionaryImportResult = withContext(Dispatchers.IO) {
        if (sources.isEmpty()) return@withContext DictionaryImportResult.Failed

        val contextResolver = SystemUserDictionaryContextIdResolver(
            idEntries = idDefEntryRepository.getEntries(),
            defaultContextId = DEFAULT_CONTEXT_ID,
        )

        val imported = LinkedHashMap<String, SystemUserDictionaryEntry>()
        var skipped = 0

        if (sources.contains(InternalImportSource.LEARN)) {
            learnRepository.allSuspend().forEach { item ->
                val yomi = normalizeYomi(item.input)
                val tango = item.out.trim()
                if (!looksLikeYomi(yomi) || tango.isBlank()) {
                    skipped += 1
                    return@forEach
                }
                val pair = if (item.leftId != null && item.rightId != null) {
                    item.leftId.toInt() to item.rightId.toInt()
                } else {
                    contextResolver.resolve("noun")
                }
                val key = "$yomi\t$tango\t${pair.first}\t${pair.second}"
                imported.putIfAbsent(
                    key,
                    SystemUserDictionaryEntry(
                        yomi = yomi,
                        tango = tango,
                        score = item.score.toInt(),
                        leftId = pair.first,
                        rightId = pair.second,
                    ),
                )
            }
        }

        if (sources.contains(InternalImportSource.USER_WORD)) {
            userDictionaryRepository.allWordsSuspend().forEach { item ->
                val yomi = normalizeYomi(item.reading)
                val tango = item.word.trim()
                if (!looksLikeYomi(yomi) || tango.isBlank()) {
                    skipped += 1
                    return@forEach
                }
                val pair = contextResolver.resolveFromPosIndex(item.posIndex)
                val key = "$yomi\t$tango\t${pair.first}\t${pair.second}"
                imported.putIfAbsent(
                    key,
                    SystemUserDictionaryEntry(
                        yomi = yomi,
                        tango = tango,
                        score = item.posScore,
                        leftId = pair.first,
                        rightId = pair.second,
                    ),
                )
            }
        }

        if (sources.contains(InternalImportSource.USER_TEMPLATE)) {
            userTemplateRepository.allTemplatesSuspend().forEach { item ->
                val yomi = normalizeYomi(item.reading)
                val tango = item.word.trim()
                if (!looksLikeYomi(yomi) || tango.isBlank()) {
                    skipped += 1
                    return@forEach
                }
                val pair = contextResolver.resolveFromPosIndex(item.posIndex)
                val key = "$yomi\t$tango\t${pair.first}\t${pair.second}"
                imported.putIfAbsent(
                    key,
                    SystemUserDictionaryEntry(
                        yomi = yomi,
                        tango = tango,
                        score = item.posScore,
                        leftId = pair.first,
                        rightId = pair.second,
                    ),
                )
            }
        }

        val entries = imported.values.toList()
        when (importMode) {
            ImportMode.APPEND -> repository.insertAll(entries)
            ImportMode.REPLACE_ALL -> repository.replaceAll(entries)
        }

        DictionaryImportResult.InternalDictionary(
            importedEntries = entries.size,
            skippedEntries = skipped,
            sourceCount = sources.size,
        )
    }

    suspend fun clearBuiltDictionary(): Boolean = withContext(Dispatchers.IO) {
        try {
            fileManager.clearAll()
            kanaKanjiEngine.loadSystemUserDictionaryFromFiles(context)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun hasBuiltDictionary(): Boolean = fileManager.hasBuiltDictionary()

    suspend fun reloadBuiltDictionaryIfExists(): Boolean = withContext(Dispatchers.IO) {
        if (!fileManager.hasBuiltDictionary()) return@withContext false
        kanaKanjiEngine.loadSystemUserDictionaryFromFiles(context)
        true
    }

    fun readBuildMetadata(): SystemUserDictionaryFileManager.BuildMetadata? = fileManager.readMetadata()

    private suspend fun tryImportBuiltDictionary(
        uri: Uri,
        importMode: ImportMode,
    ): DictionaryImportResult.BuiltDictionary? {
        return try {
            val importResult = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                fileManager.importBuiltDictionary(inputStream)
            } ?: return null

            val importedEntries = importResult.entriesJson
                ?.takeIf { it.isNotBlank() }
                ?.let { entriesJson ->
                    val type = object : TypeToken<List<SystemUserDictionaryEntry>>() {}.type
                    val imported = gson.fromJson<List<SystemUserDictionaryEntry>?>(entriesJson, type).orEmpty()
                    val normalized = imported.map { it.copy(id = 0) }
                    when (importMode) {
                        ImportMode.APPEND -> repository.insertAll(normalized)
                        ImportMode.REPLACE_ALL -> repository.replaceAll(normalized)
                    }
                    imported.size
                }

            kanaKanjiEngine.loadSystemUserDictionaryFromFiles(context)
            DictionaryImportResult.BuiltDictionary(importedEntries)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun tryImportExternalDictionary(
        uri: Uri,
        importMode: ImportMode,
        contextResolver: SystemUserDictionaryContextIdResolver,
    ): DictionaryImportResult.ExternalDictionary? {
        val parsed = try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                SystemUserDictionaryExternalParser.parse(inputStream.readBytes())
            }
        } catch (_: Exception) {
            null
        } ?: return null

        val deduplicated = LinkedHashMap<String, SystemUserDictionaryEntry>()
        parsed.entries.forEach { parsedEntry ->
            val (leftId, rightId) = contextResolver.resolve(parsedEntry.posHint)
            val key = "${parsedEntry.yomi}\t${parsedEntry.tango}\t$leftId\t$rightId"
            deduplicated.putIfAbsent(
                key,
                SystemUserDictionaryEntry(
                    yomi = parsedEntry.yomi,
                    tango = parsedEntry.tango,
                    score = DEFAULT_SCORE,
                    leftId = leftId,
                    rightId = rightId,
                ),
            )
        }

        val entries = deduplicated.values.toList()
        when (importMode) {
            ImportMode.APPEND -> repository.insertAll(entries)
            ImportMode.REPLACE_ALL -> repository.replaceAll(entries)
        }

        return DictionaryImportResult.ExternalDictionary(
            importedEntries = entries.size,
            skippedLines = parsed.skippedLines,
        )
    }

    private fun normalizeYomi(value: String): String {
        return StringBuilder(value.length).apply {
            value.forEach { ch ->
                when (ch) {
                    in 'ァ'..'ヶ' -> append((ch.code - 0x60).toChar())
                    else -> append(ch)
                }
            }
        }.toString()
    }

    private fun looksLikeYomi(value: String): Boolean {
        if (value.isBlank()) return false
        return value.all { it in 'ぁ'..'ゖ' || it == 'ー' }
    }
}
