package com.kazumaproject.markdownhelperkeyboard.system_user_dictionary.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "system_user_dictionary_entry",
    indices = [
        Index(value = ["yomi"]),
        Index(value = ["tango"]),
        Index(value = ["yomi", "tango", "leftId", "rightId"], unique = true),
    ],
)
data class SystemUserDictionaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val yomi: String,
    val tango: String,
    val score: Int,
    val leftId: Int,
    val rightId: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
