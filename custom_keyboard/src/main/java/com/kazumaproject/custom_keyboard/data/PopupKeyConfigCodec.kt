package com.kazumaproject.custom_keyboard.data

import com.google.gson.Gson

/** Validate saved and imported maps before they reach the gesture controller. */
object PopupKeyConfigCodec {
    private val gson = Gson()

    fun encode(config: PopupKeyConfig): String {
        require(PopupMapValidator.conflicts(config).isEmpty())
        return gson.toJson(config)
    }

    fun decode(json: String?): PopupKeyConfig? = runCatching {
        val config = gson.fromJson(json, PopupKeyConfig::class.java) ?: return null
        config.maps.forEach { map ->
            requireStrings(map.id, map.name, map.tapText)
            map.regions.forEach { region ->
                requireStrings(region.id, region.label, region.action.text)
                requireNotNull(region.action.type)
            }
        }
        require(config.keyId.isNotBlank() && PopupMapValidator.conflicts(config).isEmpty())
        config
    }.getOrNull()

    private fun requireStrings(vararg values: String?) { require(values.all { it != null }) }

    fun create(keyId: String, tapText: String): PopupKeyConfig = PopupKeyConfig(
        keyId, "normal", listOf(PopupMap("normal", tapText, tapText))
    )
}
