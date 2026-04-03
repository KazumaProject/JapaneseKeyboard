package com.kazumaproject.markdownhelperkeyboard.custom_romaji.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

class MapTypeConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromMap(map: Map<String, Pair<String, Int>>): String {
        val root = JsonObject()
        map.forEach { (romaji, pair) ->
            val kana = pair.first
            val consume = pair.second
            if (romaji.isBlank() || kana.isBlank()) return@forEach

            val node = JsonObject()
            node.addProperty(KEY_KANA, kana)
            node.addProperty(KEY_CONSUME, consume)
            root.add(romaji, node)
        }
        return gson.toJson(root)
    }

    @TypeConverter
    fun toMap(json: String): Map<String, Pair<String, Int>> {
        if (json.isBlank()) return emptyMap()

        val rootObject = runCatching {
            JsonParser.parseString(json).asJsonObject
        }.getOrNull() ?: return emptyMap()

        val result = linkedMapOf<String, Pair<String, Int>>()
        rootObject.entrySet().forEach { (romaji, element) ->
            if (romaji.isBlank()) return@forEach

            val parsed = parseRule(element, romaji.length)
            if (parsed != null) {
                result[romaji] = parsed
            }
        }
        return result
    }

    private fun parseRule(element: JsonElement, defaultConsume: Int): Pair<String, Int>? {
        if (!element.isJsonObject) return null
        val obj = element.asJsonObject

        // Support new format (kana/consume), legacy Pair format (first/second),
        // and obfuscated legacy keys by inferring the first string/int primitive fields.
        val kana = obj.readString(KEY_KANA)
            ?: obj.readString(KEY_FIRST)
            ?: obj.findFirstStringValue()
        if (kana.isNullOrBlank()) return null

        val consume = (
            obj.readInt(KEY_CONSUME)
                ?: obj.readInt(KEY_SECOND)
                ?: obj.findFirstIntValue()
                ?: defaultConsume
            )
            .coerceAtLeast(1)

        return kana to consume
    }

    private fun JsonObject.readString(key: String): String? {
        val value = get(key) ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) return null
        return value.asString
    }

    private fun JsonObject.readInt(key: String): Int? {
        val value = get(key) ?: return null
        if (!value.isJsonPrimitive) return null
        val primitive = value.asJsonPrimitive
        return when {
            primitive.isNumber -> runCatching { primitive.asInt }.getOrNull()
            primitive.isString -> primitive.asString.toIntOrNull()
            else -> null
        }
    }

    private fun JsonObject.findFirstStringValue(): String? {
        for ((_, value) in entrySet()) {
            if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) continue
            val text = value.asString
            if (text.isNotBlank()) return text
        }
        return null
    }

    private fun JsonObject.findFirstIntValue(): Int? {
        for ((_, value) in entrySet()) {
            if (!value.isJsonPrimitive) continue
            val primitive = value.asJsonPrimitive
            val intValue = when {
                primitive.isNumber -> runCatching { primitive.asInt }.getOrNull()
                primitive.isString -> primitive.asString.toIntOrNull()
                else -> null
            }
            if (intValue != null) return intValue
        }
        return null
    }

    private companion object {
        const val KEY_KANA = "kana"
        const val KEY_CONSUME = "consume"
        const val KEY_FIRST = "first"
        const val KEY_SECOND = "second"
    }
}
