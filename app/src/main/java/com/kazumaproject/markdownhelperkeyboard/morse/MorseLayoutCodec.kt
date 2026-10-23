package com.kazumaproject.markdownhelperkeyboard.morse

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.kazumaproject.morse_keyboard.MorseControl
import com.kazumaproject.morse_keyboard.MorseControlLayout
import com.kazumaproject.morse_keyboard.MorseControlPlacement

internal object MorseLayoutCodec {
    fun encode(layout: MorseControlLayout): String {
        layout.validate()
        return JsonObject().apply {
            addProperty("version", 1)
            addProperty("abovePads", layout.abovePads)
            addProperty("placement", layout.placement.name)
            add("rows", JsonArray().apply { layout.rows.forEach { row ->
                add(JsonArray().apply { row.forEach { add(it.name) } })
            } })
        }.toString()
    }
    fun decode(json: String): MorseControlLayout {
        require(json.length <= 4096)
        val root = JsonParser.parseString(json).asJsonObject
        require(root["version"].asString == "1" && root["version"].asJsonPrimitive.isNumber)
        return MorseControlLayout(root["rows"].asJsonArray.map { row -> row.asJsonArray.map { key ->
            require(key.asJsonPrimitive.isString)
            MorseControl.valueOf(key.asString)
        } }, root["abovePads"].asBoolean, root["placement"]?.let {
            require(it.asJsonPrimitive.isString)
            MorseControlPlacement.valueOf(it.asString)
        } ?: if (root["abovePads"].asBoolean) MorseControlPlacement.TOP else MorseControlPlacement.BOTTOM).also { it.validate() }
    }
}
