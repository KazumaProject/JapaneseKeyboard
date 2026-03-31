package com.kazumaproject.markdownhelperkeyboard.repository

import androidx.lifecycle.LiveData
import com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.database.SystemUserDictionaryDao
import com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.database.SystemUserDictionaryEntry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemUserDictionaryRepository @Inject constructor(
    private val dao: SystemUserDictionaryDao,
) {
    val allEntries: LiveData<List<SystemUserDictionaryEntry>> = dao.getAll()

    suspend fun getAllForBuild(): List<SystemUserDictionaryEntry> = dao.getAllForBuild()

    suspend fun insert(entry: SystemUserDictionaryEntry) = dao.insert(entry)

    suspend fun insertAll(entries: List<SystemUserDictionaryEntry>) = dao.insertAll(entries)

    suspend fun update(entry: SystemUserDictionaryEntry) = dao.update(entry)

    suspend fun delete(id: Int) = dao.deleteById(id)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun replaceAll(entries: List<SystemUserDictionaryEntry>) = dao.replaceAll(entries)
}
