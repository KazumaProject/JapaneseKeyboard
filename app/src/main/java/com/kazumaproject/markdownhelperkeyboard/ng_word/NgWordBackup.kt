package com.kazumaproject.markdownhelperkeyboard.ng_word

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWord
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWordMatchMode

/** The same JSON codec is used by the settings UI and compatibility tests. */
internal object NgWordBackup {
    fun toJson(words: List<NgWord>): String = Gson().toJson(words)

    fun fromJson(json: String): List<NgWord> {
        val type = object : TypeToken<List<NgWordBackupEntry>>() {}.type
        val entries: List<NgWordBackupEntry> = Gson().fromJson(json, type)
        return entries.mapNotNull { entry ->
            val yomi = entry.yomi?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val tango = entry.tango?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            NgWord(
                yomi = yomi,
                tango = tango,
                matchMode = NgWordMatchMode.fromStorage(entry.matchMode),
            )
        }
    }

    private data class NgWordBackupEntry(
        val yomi: String? = null,
        val tango: String? = null,
        val matchMode: String? = null,
    )
}
