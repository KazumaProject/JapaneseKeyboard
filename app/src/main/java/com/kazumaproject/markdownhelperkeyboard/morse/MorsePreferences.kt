package com.kazumaproject.markdownhelperkeyboard.morse

import android.content.Context
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kazumaproject.morse_keyboard.*
import java.util.UUID

class MorsePreferences(baseContext: Context) {
    private val context = localizedMorseContext(baseContext)
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val gson = Gson()
    private fun number(key: String, default: Int, min: Int, max: Int) = prefs.getInt(key, default).coerceIn(min, max)
    fun controlLayout(): MorseControlLayout = runCatching {
        prefs.getString("morse_key_layout_v1", null)?.let(MorseLayoutCodec::decode) ?: MorseControlLayout()
    }.getOrDefault(MorseControlLayout())
    fun saveControlLayout(layout: MorseControlLayout) { prefs.edit().putString("morse_key_layout_v1", MorseLayoutCodec.encode(layout)).apply() }
    fun saveReverse(reverse: Boolean) { prefs.edit().putBoolean("morse_reverse", reverse).apply() }
    fun settings() = MorseSettings(
        beginnerGuide = prefs.getBoolean("morse_beginner_guide", false),
        controlLayout = controlLayout(),
        style = runCatching { MorseInputStyle.valueOf(prefs.getString("morse_style", "PADDLE_A")!!) }.getOrDefault(MorseInputStyle.PADDLE_A),
        wpm = number("morse_wpm", 15, 5, 60),
        autoBoundary = prefs.getBoolean("morse_auto_boundary", true),
        boundaryMs = number("morse_boundary_ms", 600, 100, 3000),
        straightThresholdMs = number("morse_straight_threshold_ms", 160, 60, 1000),
        reverse = prefs.getBoolean("morse_reverse", false),
        padWidthPercent = number("morse_pad_width", 100, 40, 100),
        padHeightPercent = number("morse_pad_height", 100, 40, 100),
        gapDp = number("morse_pad_gap", 8, 0, 48),
        oneHand = prefs.getBoolean("morse_one_hand", false),
        sound = prefs.getBoolean("morse_sound", true),
        frequencyHz = number("morse_frequency", 780, 200, 1500),
        volumePercent = number("morse_volume", 25, 0, 100),
        vibration = prefs.getBoolean("morse_vibration", false),
    )
    private fun ids(key: String): List<String>? = runCatching {
        prefs.getString(key, null)?.let { gson.fromJson<List<String>>(it, object : TypeToken<List<String>>() {}.type) }
    }.getOrNull()

    private fun customMaps(): List<MorseMap> = runCatching {
        prefs.getString("morse_custom_maps_v1", null)?.let(MorseMapCodec::decode).orEmpty()
            .filterNot { it.id.startsWith("builtin_") }
    }.getOrDefault(emptyList())

    fun allMaps(): List<MorseMap> {
        val all = MorsePresets.maps(context.resources.configuration.locales[0].language == "ja") + customMaps()
        val byId = all.associateBy { it.id }
        val order = (ids("morse_map_order").orEmpty() + all.map { it.id }).distinct()
        return order.mapNotNull { byId[it] }
    }
    fun enabledIds(): Set<String> {
        val all = allMaps().map { it.id }
        return (ids("morse_enabled_maps") ?: all).filter { it in all }.toSet().ifEmpty { setOf(all.first()) }
    }
    fun enabledMaps(): List<MorseMap> = allMaps().let { all ->
        all.filter { it.id in enabledIds() }.ifEmpty { listOf(all.first()) }
    }
    var selectedMapId: String
        get() = enabledMaps().let { maps -> prefs.getString("morse_selected_map", null)?.takeIf { id -> maps.any { it.id == id } } ?: maps.first().id }
        set(value) { require(enabledMaps().any { it.id == value }); prefs.edit().putString("morse_selected_map", value).apply() }

    fun saveOrder(order: List<String>, enabled: Set<String>) {
        val allIds = allMaps().map { it.id }.toSet()
        require(enabled.any { it in allIds }) { "Enable at least one map" }
        prefs.edit().putString("morse_map_order", gson.toJson(order.distinct().filter { it in allIds }))
            .putString("morse_enabled_maps", gson.toJson(enabled.filter { it in allIds })).apply()
    }
    fun saveMap(map: MorseMap) {
        require(!map.builtIn && !map.id.startsWith("builtin_"))
        map.validate()
        val enabled = enabledIds() + map.id
        val order = allMaps().map { it.id } + map.id
        val maps = customMaps().filterNot { it.id == map.id } + map
        prefs.edit().putString("morse_custom_maps_v1", MorseMapCodec.encode(maps))
            .putString("morse_map_order", gson.toJson(order.distinct()))
            .putString("morse_enabled_maps", gson.toJson(enabled))
            .putString("morse_selected_map", map.id).apply()
    }
    fun deleteMap(id: String) {
        require(!id.startsWith("builtin_"))
        val remaining = allMaps().filterNot { it.id == id }
        val enabled = (enabledIds() - id).ifEmpty { setOf(remaining.first().id) }
        prefs.edit().putString("morse_custom_maps_v1", MorseMapCodec.encode(customMaps().filterNot { it.id == id }))
            .putString("morse_map_order", gson.toJson(remaining.map { it.id }))
            .putString("morse_enabled_maps", gson.toJson(enabled)).apply()
    }
    /** Parse and validate the whole import before changing any preference. */
    fun importMaps(json: String) {
        val imported = MorseMapCodec.decode(json).map { it.copy(id = UUID.randomUUID().toString(), builtIn = false) }
        require(imported.isNotEmpty()) { "No maps in file" }
        val maps = customMaps() + imported
        val order = allMaps().map { it.id } + imported.map { it.id }
        val enabled = enabledIds() + imported.map { it.id }
        prefs.edit().putString("morse_custom_maps_v1", MorseMapCodec.encode(maps))
            .putString("morse_map_order", gson.toJson(order))
            .putString("morse_enabled_maps", gson.toJson(enabled)).apply()
    }
}
