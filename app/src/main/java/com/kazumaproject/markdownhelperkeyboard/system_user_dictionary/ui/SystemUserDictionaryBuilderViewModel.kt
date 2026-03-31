package com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.ui

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.repository.SystemUserDictionaryRepository
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
    private val idDefEntryRepository: IdDefEntryRepository,
    private val systemUserDictionaryBuilder: SystemUserDictionaryBuilder,
    private val fileManager: SystemUserDictionaryFileManager,
    private val kanaKanjiEngine: KanaKanjiEngine,
    @ApplicationContext private val context: Context,
) : ViewModel() {

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

    suspend fun reloadBuiltDictionaryIfExists(): Boolean = withContext(Dispatchers.IO) {
        if (!fileManager.hasBuiltDictionary()) return@withContext false
        kanaKanjiEngine.loadSystemUserDictionaryFromFiles(context)
        true
    }

    fun readBuildMetadata(): SystemUserDictionaryFileManager.BuildMetadata? = fileManager.readMetadata()
}
