package com.kazumaproject.markdownhelperkeyboard.gesture

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.kazumaproject.gesture_keyboard.*

/** Explicit wire names remain stable in minified builds. */
object GestureCodec {
    private const val LIMIT = 2 * 1024 * 1024
    private fun root(json: String): JsonObject {
        require(json.length <= LIMIT) { "Gesture data is too large" }
        return JsonParser.parseString(json).asJsonObject.also {
            require(it["version"]?.asJsonPrimitive?.isNumber == true && it["version"].asString == "1") { "Unsupported gesture version" }
        }
    }
    private fun JsonObject.string(key: String): String = get(key).also {
        require(it != null && it.isJsonPrimitive && it.asJsonPrimitive.isString) { "Expected string: $key" }
    }.asString
    private fun document() = JsonObject().apply { addProperty("version", 1) }
    fun encodeSets(sets: List<GestureSet>): String {
        sets.forEach { require(!it.builtIn && !it.id.startsWith("builtin_")); it.validate() }
        return document().apply { add("sets", JsonArray().apply { sets.forEach { set -> add(JsonObject().apply {
            addProperty("id", set.id); addProperty("name", set.name)
            add("entries", JsonArray().apply { set.entries.forEach { entry -> add(JsonObject().apply {
                addProperty("id", entry.id)
                when (val output = entry.output) {
                    is GestureOutput.Reading -> { addProperty("type", "reading"); addProperty("value", output.text) }
                    is GestureOutput.LiteralText -> { addProperty("type", "text"); addProperty("value", output.text) }
                    is GestureOutput.Command -> { addProperty("type", "command"); addProperty("value", output.action.name) }
                }
                add("samples", JsonArray().apply { entry.samples.forEach { sample -> add(JsonArray().apply {
                    sample.forEach { point -> add(JsonArray().apply { add(point.x); add(point.y) }) }
                }) } })
            }) } })
        }) } }) }.toString().also { require(it.length <= LIMIT) }
    }
    fun decodeSets(json: String): List<GestureSet> {
        val sets = root(json)["sets"].asJsonArray.also { require(it.size() <= 64) }.map { element ->
            val set = element.asJsonObject
            val id = set.string("id"); require(!id.startsWith("builtin_"))
            val entries = set["entries"].asJsonArray.also { require(it.size() <= 256) }.map { entryElement ->
                val entry = entryElement.asJsonObject
                val value = entry.string("value")
                val output = when (entry.string("type")) {
                    "reading" -> GestureOutput.Reading(value)
                    "text" -> GestureOutput.LiteralText(value)
                    "command" -> GestureOutput.Command(GestureCommand.valueOf(value))
                    else -> error("Unknown gesture output")
                }
                val samples = entry["samples"].asJsonArray.also { require(it.size() in 1..16) }.map { sample ->
                    sample.asJsonArray.also { require(it.size() in 2..512) }.map { point ->
                        point.asJsonArray.also { require(it.size() == 2 && it.all { v -> v.isJsonPrimitive && v.asJsonPrimitive.isNumber }) }
                            .let { GesturePoint(it[0].asFloat, it[1].asFloat) }
                    }
                }
                GestureEntry(entry.string("id"), output, samples)
            }
            GestureSet(id, set.string("name"), entries).also { it.validate() }
        }
        require(sets.map { it.id }.distinct().size == sets.size)
        return sets
    }
    fun encodeLayout(layout: GestureControlLayout): String {
        layout.validate()
        return document().apply {
            GestureSide.entries.forEach { side -> add(side.name.lowercase(), JsonArray().apply {
                layout.keys(side).forEach { key -> add(JsonObject().apply {
                    addProperty("id", key.id); addProperty("action", key.action.name)
                    key.labelOverride?.let { addProperty("label", it) }
                }) }
            }) }
        }.toString()
    }
    fun decodeLayout(json: String): GestureControlLayout {
        val root = root(json)
        fun keys(name: String) = root[name].asJsonArray.also { require(it.size() <= 128) }.map { value ->
            val key = value.asJsonObject
            GestureControlKey(key.string("id"), GestureCommand.valueOf(key.string("action")),
                if (key.has("label")) key.string("label") else null)
        }
        return GestureControlLayout(keys("left"), keys("right")).also { it.validate() }
    }
    fun encodeSession(state: GestureSessionState) = document().apply {
        addProperty("left", state.leftSetId); addProperty("right", state.rightSetId)
        addProperty("leftUppercase", state.leftUppercase); addProperty("rightUppercase", state.rightUppercase)
    }.toString()
    fun decodeSession(json: String): GestureSessionState = root(json).let {
        require(it["leftUppercase"].asJsonPrimitive.isBoolean && it["rightUppercase"].asJsonPrimitive.isBoolean)
        GestureSessionState(it.string("left"), it.string("right"), it["leftUppercase"].asBoolean, it["rightUppercase"].asBoolean)
    }
}
