package com.kazumaproject.markdownhelperkeyboard.gemma.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gemma_prompt_template",
    indices = [
        Index(value = ["sortOrder"]),
        Index(value = ["isEnabled"]),
    ]
)
data class GemmaPromptTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val prompt: String,
    val isEnabled: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)
