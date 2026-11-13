package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.content.Context
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.kazumaproject.custom_keyboard.data.PopupKeyConfig
import com.kazumaproject.custom_keyboard.data.PopupMapValidator
import com.kazumaproject.custom_keyboard.data.SumirePopupDefaults

/** One atomic value per key; included in the existing preferences backup. */
class SumirePopupRepository(private val context: Context) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    private val gson = Gson()
    fun load(key: String, layoutId: String? = null): PopupKeyConfig = if (layoutId == null) decode(preferences.getString(PREFIX + key, null), key)
        else FreeFlickLayoutRepository(context).find(layoutId).keys.first { it.keyId == key }
    fun hasInvalidSavedConfig(key: String, layoutId: String? = null): Boolean {
        if (layoutId != null && layoutId != FreeFlickLayoutRepository(context).selectedId()) return false
        val json = preferences.getString(PREFIX + key, null) ?: return false
        return runCatching { val value = gson.fromJson(json, PopupKeyConfig::class.java); value.keyId == key && PopupMapValidator.conflicts(value).isEmpty() }.getOrDefault(false).not()
    }
    fun save(config: PopupKeyConfig, layoutId: String? = null): String {
        require(config.keyId in SumirePopupDefaults.rows)
        require(PopupMapValidator.conflicts(config).isEmpty())
        return FreeFlickLayoutRepository(context).saveKey(config, layoutId)
    }
    fun encode(config: PopupKeyConfig): String = gson.toJson(config)
    fun decode(json: String?, key: String): PopupKeyConfig = runCatching {
        val config = gson.fromJson(json, PopupKeyConfig::class.java)
        require(config.keyId == key && PopupMapValidator.conflicts(config).isEmpty())
        config
    }.getOrElse { SumirePopupDefaults.create(key) }
    companion object { const val PREFIX = "sumire_popup_maps_v1_" }
}
