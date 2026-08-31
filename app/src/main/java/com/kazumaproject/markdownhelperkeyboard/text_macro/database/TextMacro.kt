package com.kazumaproject.markdownhelperkeyboard.text_macro.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "text_macro",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["reading"]),
    ],
)
data class TextMacro(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val reading: String? = null,
    val body: String,
    val enabled: Boolean = true,
)

