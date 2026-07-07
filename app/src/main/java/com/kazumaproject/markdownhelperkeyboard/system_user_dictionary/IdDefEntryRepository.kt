package com.kazumaproject.markdownhelperkeyboard.system_user_dictionary

import android.content.Context
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryBinaryReader
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IdDefEntryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dictionaryBinaryReader: DictionaryBinaryReader,
) {
    @Volatile
    private var cachedEntries: List<IdDefEntry>? = null

    fun getEntries(): List<IdDefEntry> {
        cachedEntries?.let { return it }
        return synchronized(this) {
            cachedEntries ?: loadEntries().also { cachedEntries = it }
        }
    }

    fun findById(id: Int): IdDefEntry? = getEntries().firstOrNull { it.id == id }

    fun clearCache() {
        synchronized(this) {
            cachedEntries = null
        }
    }

    private fun loadEntries(): List<IdDefEntry> {
        return dictionaryBinaryReader.openIdDefReader(DictionaryFileKey.ID_DEF).useLines { lines ->
            lines.mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) {
                    null
                } else {
                    val firstSpace = trimmed.indexOf(' ')
                    if (firstSpace <= 0) {
                        null
                    } else {
                        val id = trimmed.substring(0, firstSpace).toIntOrNull() ?: return@mapNotNull null
                        val label = trimmed.substring(firstSpace + 1).trim()
                        IdDefEntry(id = id, label = label)
                    }
                }
            }.toList()
        }
    }
}
