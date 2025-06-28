package com.kazumaproject.markdownhelperkeyboard.clipboard_history.database

import androidx.room.TypeConverter

class ItemTypeConverter {
    @TypeConverter
    fun fromItemType(value: ItemType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toItemType(value: String?): ItemType? {
        return value?.let { ItemType.valueOf(it) }
    }
}
