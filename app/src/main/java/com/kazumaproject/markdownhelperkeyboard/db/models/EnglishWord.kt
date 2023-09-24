package com.kazumaproject.markdownhelperkeyboard.db.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dictionary_english_word_table")
data class EnglishWord(
    val word: String,
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0
)