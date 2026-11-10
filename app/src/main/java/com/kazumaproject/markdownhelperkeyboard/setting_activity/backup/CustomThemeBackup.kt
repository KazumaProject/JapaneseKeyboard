package com.kazumaproject.markdownhelperkeyboard.setting_activity.backup

import android.content.SharedPreferences
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.Strictness

internal class CustomThemeBackup(
    preferenceDefaults: List<Pair<String, Any>>,
    private val integerRanges: Map<String, IntRange>,
    private val stringValues: Map<String, Set<String>>,
) {
    private val fields = preferenceDefaults.toMap()
    private val gson = GsonBuilder()
        .setStrictness(Strictness.STRICT)
        .serializeNulls()
        .setPrettyPrinting()
        .create()

    fun export(preferences: SharedPreferences): String {
        val stored = preferences.all
        val theme = JsonObject()
        fields.forEach { (key, defaultValue) ->
            val value = stored[key]
            when (defaultValue) {
                is Boolean -> theme.addProperty(key, value as? Boolean)
                is Int -> {
                    val number = value as? Int
                    val range = integerRanges[key]
                    theme.addProperty(key, if (range == null) number else number?.coerceIn(range))
                }
                is String -> theme.addProperty(
                    key,
                    (value as? String)?.takeIf { it in stringValues.getValue(key) }
                )
            }
        }
        return gson.toJson(JsonObject().apply {
            addProperty("format", FORMAT)
            addProperty("version", VERSION)
            add("preferences", theme)
        })
    }

    fun restore(json: String, preferences: SharedPreferences) {
        val backup = requireNotNull(gson.fromJson(json, JsonObject::class.java))
        require(backup["format"] == JsonPrimitive(FORMAT))
        val version = backup["version"] as? JsonPrimitive
        require(version?.isNumber == true && version.asString.toIntOrNull() == VERSION)
        val theme = requireNotNull(backup["preferences"] as? JsonObject)
        require(theme.keySet() == fields.keys)

        // Validate all values before writing. Null restores the preference's normal fallback.
        val values = fields.mapValues { (key, defaultValue) ->
            val value = theme[key]
            if (value.isJsonNull) return@mapValues null
            require(value is JsonPrimitive)
            when (defaultValue) {
                is Boolean -> {
                    require(value.isBoolean)
                    value.asBoolean
                }
                is Int -> {
                    require(value.isNumber)
                    val number = requireNotNull(value.asString.toIntOrNull())
                    require(integerRanges[key]?.contains(number) != false)
                    number
                }
                is String -> {
                    require(value.isString && value.asString in stringValues.getValue(key))
                    value.asString
                }
                else -> error("Unsupported theme preference type for $key")
            }
        }
        val editor = preferences.edit()
        values.forEach { (key, value) ->
            when (value) {
                null -> editor.remove(key)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is String -> editor.putString(key, value)
            }
        }
        editor.apply()
    }

    private companion object {
        const val FORMAT = "japanese-keyboard-custom-theme"
        const val VERSION = 1
    }
}
