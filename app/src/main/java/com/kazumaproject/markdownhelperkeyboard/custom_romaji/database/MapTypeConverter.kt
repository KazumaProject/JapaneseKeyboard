package com.kazumaproject.markdownhelperkeyboard.custom_romaji.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MapTypeConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromMap(map: Map<String, Pair<String, Int>>): String {
        return gson.toJson(map)
    }

    @TypeConverter
    fun toMap(json: String): Map<String, Pair<String, Int>> {
        val type = object : TypeToken<Map<String, Pair<String, Int>>>() {}.type
        return gson.fromJson(json, type)
    }
}
