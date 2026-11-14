package com.kazumaproject.markdownhelperkeyboard.ime_service.voice_input

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName

data class VoiceInputReplacementRule(
    @SerializedName("source") val source: String,
    @SerializedName("replacement") val replacement: String,
)

object VoiceInputReplacementRuleCodec {
    fun encode(rules: List<VoiceInputReplacementRule>): String = Gson().toJson(rules)

    fun decode(json: String): List<VoiceInputReplacementRule> = runCatching {
        JsonParser.parseString(json).asJsonArray.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val source = element.asJsonObject.get("source")
            val replacement = element.asJsonObject.get("replacement")
            if (source == null || !source.isJsonPrimitive || !source.asJsonPrimitive.isString ||
                replacement == null || !replacement.isJsonPrimitive || !replacement.asJsonPrimitive.isString ||
                source.asString.isEmpty()
            ) return@mapNotNull null
            VoiceInputReplacementRule(source.asString, replacement.asString)
        }.distinctBy { it.source }
    }.getOrDefault(emptyList())
}
