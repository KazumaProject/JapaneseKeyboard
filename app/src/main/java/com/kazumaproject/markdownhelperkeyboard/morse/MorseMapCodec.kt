package com.kazumaproject.markdownhelperkeyboard.morse

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.kazumaproject.morse_keyboard.*

object MorseMapCodec {
    private const val MAX_JSON_LENGTH = 1_048_576
    private fun JsonObject.requiredString(key: String): String {
        val value = get(key)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) { "Expected string: $key" }
        return value.asString
    }
    fun encode(maps: List<MorseMap>): String {
        maps.forEach { it.validate() }
        val root = JsonObject().apply {
            addProperty("version", 1)
            add("maps", JsonArray().apply { maps.forEach { map -> add(JsonObject().apply {
                addProperty("id", map.id); addProperty("name", map.name)
                add("entries", JsonArray().apply { map.entries.forEach { entry -> add(JsonObject().apply {
                    addProperty("signal", entry.signal)
                    val (type, value) = when (val output = entry.output) {
                        is MorseOutput.Reading -> "reading" to output.text
                        is MorseOutput.Romaji -> "romaji" to output.text
                        is MorseOutput.LiteralText -> "text" to output.text
                        is MorseOutput.Command -> "command" to output.action.name
                    }
                    addProperty("type", type); addProperty("value", value)
                }) } })
            }) } })
        }
        return GsonBuilder().setPrettyPrinting().create().toJson(root).also {
            require(it.length <= MAX_JSON_LENGTH) { "Map file is too large" }
        }
    }

    fun decode(json: String): List<MorseMap> {
        require(json.length <= MAX_JSON_LENGTH) { "Map file is too large" }
        val root = JsonParser.parseString(json).asJsonObject
        val version = root["version"]
        require(version != null && version.isJsonPrimitive && version.asJsonPrimitive.isNumber && version.asString == "1") { "Unsupported map version" }
        val maps = root["maps"].asJsonArray.map { element ->
            val map = element.asJsonObject
            MorseMap(map.requiredString("id"), map.requiredString("name"), map["entries"].asJsonArray.map { item ->
                val entry = item.asJsonObject
                val value = entry.requiredString("value")
                MorseEntry(entry.requiredString("signal"), when (entry.requiredString("type")) {
                    "reading" -> MorseOutput.Reading(value)
                    "romaji" -> MorseOutput.Romaji(value)
                    "text" -> MorseOutput.LiteralText(value)
                    "command" -> MorseOutput.Command(MorseCommand.valueOf(value))
                    else -> throw IllegalArgumentException("Unknown output type")
                })
            }).also { it.validate() }
        }
        require(maps.map { it.id }.distinct().size == maps.size) { "Duplicate map ID" }
        return maps
    }
}
