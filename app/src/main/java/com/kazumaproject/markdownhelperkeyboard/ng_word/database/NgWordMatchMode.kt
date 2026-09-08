package com.kazumaproject.markdownhelperkeyboard.ng_word.database

import androidx.room.TypeConverter

enum class NgWordMatchMode {
    PARTIAL,
    EXACT;

    companion object {
        fun fromStorage(value: String?): NgWordMatchMode =
            value?.let { runCatching { valueOf(it) }.getOrNull() } ?: PARTIAL
    }
}

class NgWordMatchModeConverter {
    @TypeConverter
    fun fromMatchMode(value: NgWordMatchMode): String = value.name

    @TypeConverter
    fun toMatchMode(value: String): NgWordMatchMode = NgWordMatchMode.fromStorage(value)
}
