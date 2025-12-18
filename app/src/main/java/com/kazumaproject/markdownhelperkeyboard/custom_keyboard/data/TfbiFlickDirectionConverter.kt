package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data

import androidx.room.TypeConverter
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection

class TfbiFlickDirectionConverter {

    @TypeConverter
    fun fromTfbiFlickDirection(value: TfbiFlickDirection?): String? {
        return value?.name
    }

    @TypeConverter
    fun toTfbiFlickDirection(value: String?): TfbiFlickDirection? {
        return value?.let { TfbiFlickDirection.valueOf(it) }
    }
}
