package com.kazumaproject.markdownhelperkeyboard.gemma.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gemma_prompt_template",
    indices = [
        Index(value = ["sortOrder"]),
        Index(value = ["isEnabled"]),
        Index(value = ["inputModality", "isEnabled"]),
        Index(value = ["builtInKey"], unique = true),
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
    val inputModality: String = GemmaInputModality.TEXT.name,
    val taskKind: String = GemmaTaskKind.CUSTOM.name,
    val outputMode: String = GemmaOutputMode.SINGLE_TEXT.name,
    val outputLanguage: String = GemmaOutputLanguage.AUTO.name,
    val candidateCount: Int = 1,
    val showInActionMenu: Boolean = true,
    val isBuiltIn: Boolean = false,
    val builtInKey: String? = null,
)

enum class GemmaInputModality {
    TEXT,
    IMAGE,
    AUDIO,
}

enum class GemmaTaskKind {
    CUSTOM,
    EXTRACT_TEXT,
    TRANSLATE,
    SUMMARIZE,
    REPLY,
    DICTATE,
}

enum class GemmaOutputMode {
    SINGLE_TEXT,
    MULTILINE_TEXT,
    CANDIDATE_LIST,
}

enum class GemmaOutputLanguage {
    AUTO,
    JAPANESE,
    ENGLISH,
}

fun GemmaPromptTemplate.modality(): GemmaInputModality =
    enumValueOfOrDefault(inputModality, GemmaInputModality.TEXT)

fun GemmaPromptTemplate.output(): GemmaOutputMode =
    enumValueOfOrDefault(outputMode, GemmaOutputMode.SINGLE_TEXT)

private inline fun <reified T : Enum<T>> enumValueOfOrDefault(value: String, fallback: T): T {
    return enumValues<T>().firstOrNull { it.name == value } ?: fallback
}
