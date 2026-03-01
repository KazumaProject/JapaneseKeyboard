package com.kazumaproject.markdownhelperkeyboard.setting_activity.backup

data class PrefEntry(
    val key: String,
    val type: String, // "boolean" | "int" | "long" | "float" | "string" | "string_set" | "null"
    val value: Any?,  // string_set の場合は List<String>
)
