package com.kazumaproject.markdownhelperkeyboard.gesture

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.kazumaproject.gesture_keyboard.*
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper

/** Explicit wire names remain stable in minified builds. */
object GestureCodec {
    private const val LIMIT = 2 * 1024 * 1024
    private fun root(json: String): JsonObject {
        require(json.length <= LIMIT) { "Gesture data is too large" }
        return JsonParser.parseString(json).asJsonObject.also {
            require(it["version"]?.asJsonPrimitive?.isNumber == true && it["version"].asString in listOf("1", "2")) { "Unsupported gesture version" }
        }
    }
    private fun JsonObject.string(key: String): String = get(key).also {
        require(it != null && it.isJsonPrimitive && it.asJsonPrimitive.isString) { "Expected string: $key" }
    }.asString
    private fun document() = JsonObject().apply { addProperty("version", 2) }
    fun encodeSets(sets: List<GestureSet>): String {
        sets.forEach { require(!it.builtIn && !it.id.startsWith("builtin_")); it.validate() }
        return document().apply { add("sets", JsonArray().apply { sets.forEach { set -> add(JsonObject().apply {
            addProperty("id", set.id); addProperty("name", set.name); addProperty("isRomaji", set.isRomaji)
            add("entries", JsonArray().apply { set.entries.forEach { entry -> add(JsonObject().apply {
                addProperty("id", entry.id)
                when (val output = entry.output) {
                    is GestureOutput.Reading -> { addProperty("type", "reading"); addProperty("value", output.text) }
                    is GestureOutput.Romaji -> error("Romaji is a runtime output, not a stored mapping")
                    is GestureOutput.LiteralText -> { addProperty("type", "text"); addProperty("value", output.text) }
                    is GestureOutput.Command -> { addProperty("type", "command"); addProperty("value", requireNotNull(KeyActionMapper.fromKeyAction(output.action))) }
                }
                add("samples", JsonArray().apply { entry.samples.forEach { sample -> add(JsonArray().apply {
                    sample.forEach { point -> add(JsonArray().apply { add(point.x); add(point.y) }) }
                }) } })
            }) } })
        }) } }) }.toString().also { require(it.length <= LIMIT) }
    }
    fun decodeSets(json: String): List<GestureSet> {
        val document = root(json)
        val sets = document["sets"].asJsonArray.also { require(it.size() <= 64) }.map { element ->
            val set = element.asJsonObject
            val id = set.string("id"); require(!id.startsWith("builtin_"))
            val entries = set["entries"].asJsonArray.also { require(it.size() <= 256) }.map { entryElement ->
                val entry = entryElement.asJsonObject
                val value = entry.string("value")
                val output = when (entry.string("type")) {
                    "reading" -> GestureOutput.Reading(value)
                    "text" -> GestureOutput.LiteralText(value)
                    "command" -> GestureOutput.Command(decodeAction(value, document["version"].asInt))
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
            GestureSet(id, set.string("name"), entries, isRomaji = if (set.has("isRomaji")) set.boolean("isRomaji") else false).also { it.validate() }
        }
        require(sets.map { it.id }.distinct().size == sets.size)
        return sets
    }
    fun encodeLayout(layout: GestureControlLayout): String {
        layout.validate()
        return document().apply {
            GestureSide.entries.forEach { side -> add(side.name.lowercase(), JsonArray().apply {
                layout.keys(side).forEach { key -> add(JsonObject().apply {
                    addProperty("id", key.id); addProperty("action", requireNotNull(KeyActionMapper.fromKeyAction(key.action)))
                    key.labelOverride?.let { addProperty("label", it) }
                }) }
            }) }
        }.toString()
    }
    fun decodeLayout(json: String): GestureControlLayout {
        val root = root(json)
        fun keys(name: String) = root[name].asJsonArray.also { require(it.size() <= 128) }.map { value ->
            val key = value.asJsonObject
            GestureControlKey(key.string("id"), decodeAction(key.string("action"), root["version"].asInt),
                if (key.has("label")) key.string("label") else null)
        }
        return GestureControlLayout(keys("left"), keys("right")).also { it.validate() }
    }
    private fun JsonObject.boolean(key: String): Boolean {
        require(get(key)?.asJsonPrimitive?.isBoolean == true) { "Expected boolean: $key" }
        return get(key).asBoolean
    }
    private fun JsonObject.number(key: String): Float {
        require(get(key)?.asJsonPrimitive?.isNumber == true) { "Expected number: $key" }
        return get(key).asFloat.also { require(it.isFinite()) }
    }
    private fun decodeAction(value: String, version: Int): KeyAction = if (version == 1) when (value) {
        "DELETE" -> KeyAction.Delete
        "SPACE" -> KeyAction.CommitAndInsertSpace
        "CONVERT" -> KeyAction.Convert
        "COMMIT" -> KeyAction.Confirm
        "ENTER" -> KeyAction.Enter
        "CURSOR_LEFT" -> KeyAction.MoveCursorLeft
        "CURSOR_RIGHT" -> KeyAction.MoveCursorRight
        "DAKUTEN" -> KeyAction.ToggleDakutenOnly
        "HANDAKUTEN" -> KeyAction.ToggleHandakutenOnly
        "SMALL_KANA" -> KeyAction.InputText("ひらがな小文字")
        "NEXT_KEYBOARD" -> KeyAction.SwitchToNextIme
        else -> error("Unknown legacy gesture action")
    } else requireNotNull(KeyActionMapper.toKeyAction(value)) { "Unknown gesture action" }

    fun encodeSession(state: GestureSessionState): String {
        state.validate()
        return document().apply {
            addProperty("activePadId", state.activePadId)
            add("pads", JsonArray().apply { state.pads.forEach { pad -> add(JsonObject().apply {
                addProperty("id", pad.id); addProperty("setId", pad.setId)
                addProperty("x", pad.bounds.x); addProperty("y", pad.bounds.y)
                addProperty("width", pad.bounds.width); addProperty("height", pad.bounds.height)
                addProperty("uppercase", pad.uppercase); addProperty("shift", pad.shift); addProperty("direct", pad.direct)
                pad.romajiOverride?.let { addProperty("romajiOverride", it) }
            }) } })
        }.toString()
    }
    fun decodeSession(json: String): GestureSessionState = root(json).let { document ->
        if (document["version"].asInt == 1) {
            GestureSessionState(pads = GestureSessionState.defaults(2).mapIndexed { index, pad ->
                val side = if (index == 0) "left" else "right"
                pad.copy(setId = document.string(side), uppercase = document.boolean(side + "Uppercase"))
            })
        } else {
            val pads = document["pads"].asJsonArray.also { require(it.size() in 1..4) }.map { value ->
                val pad = value.asJsonObject
                GesturePadState(pad.string("id"), pad.string("setId"),
                    GestureBounds(pad.number("x"), pad.number("y"), pad.number("width"), pad.number("height")),
                    pad.boolean("uppercase"), pad.boolean("shift"), pad.boolean("direct"),
                    if (pad.has("romajiOverride")) pad.boolean("romajiOverride") else null)
            }
            GestureSessionState(pads, document.string("activePadId"))
        }
    }.also { it.validate() }
}
