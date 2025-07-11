package com.kazumaproject.markdownhelperkeyboard.ng_word.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ng_word",
    indices = [Index(value = ["yomi"])]
)
data class NgWord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val yomi: String,
    val tango: String
)
