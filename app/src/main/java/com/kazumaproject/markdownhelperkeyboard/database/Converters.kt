package com.kazumaproject.markdownhelperkeyboard.database

import androidx.room.TypeConverter
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyType

class Converters {
    @TypeConverter
    fun fromKeyType(value: KeyType): String {
        return value.name
    }

    @TypeConverter
    fun toKeyType(value: String): KeyType {
        return KeyType.valueOf(value)
    }

    @TypeConverter
    fun fromFlickDirection(value: FlickDirection): String {
        return value.name
    }

    @TypeConverter
    fun toFlickDirection(value: String): FlickDirection {
        return FlickDirection.valueOf(value)
    }
}
