package com.kazumaproject.markdownhelperkeyboard.ime_service.fever

import android.content.SharedPreferences
import org.json.JSONArray

/** Independent of the legacy appearance preferences. No typed text is persisted here. */
data class FeverSettings(
    val fullScreen: Boolean = false,
    val intensity: Int = 2,
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val volume: Int = 50,
    val hitRate: Int = 5,
    val comboTimeoutMillis: Long = 3000,
    val keywordsEnabled: Boolean = true,
    val keywords: List<String> = DEFAULT_KEYWORDS,
) {
    companion object {
        const val ROUTE = "fever_settings"
        const val PREFIX = "fever_"
        const val FULL_SCREEN = "fever_full_screen"
        const val INTENSITY = "fever_intensity"
        const val SOUND = "fever_sound"
        const val VIBRATION = "fever_vibration"
        const val VOLUME = "fever_volume"
        const val HIT_RATE = "fever_hit_rate"
        const val COMBO_SECONDS = "fever_combo_seconds"
        const val KEYWORDS_ENABLED = "fever_keywords_enabled"
        const val KEYWORDS = "fever_keywords"
        val DEFAULT_KEYWORDS = listOf("ありがとう", "好き", "おめでとう", "最高", "大当たり")

        fun read(prefs: SharedPreferences): FeverSettings = FeverSettings(
            fullScreen = prefs.getBoolean(FULL_SCREEN, false),
            intensity = prefs.getInt(INTENSITY, 2).coerceIn(0, 2),
            sound = prefs.getBoolean(SOUND, true), vibration = prefs.getBoolean(VIBRATION, true),
            volume = prefs.getInt(VOLUME, 50).coerceIn(0, 100),
            hitRate = prefs.getInt(HIT_RATE, 5).coerceIn(0, 100),
            comboTimeoutMillis = prefs.getInt(COMBO_SECONDS, 3).coerceIn(1, 10) * 1000L,
            keywordsEnabled = prefs.getBoolean(KEYWORDS_ENABLED, true),
            keywords = decodeKeywords(prefs.getString(KEYWORDS, null)),
        )
        fun decodeKeywords(value: String?): List<String> = if (value == null) DEFAULT_KEYWORDS else
            runCatching {
                val array = JSONArray(value)
                (0 until array.length()).map { array.getString(it).trim() }.filter { it.isNotEmpty() }.distinct()
            }.getOrDefault(DEFAULT_KEYWORDS)
        fun saveKeywords(prefs: SharedPreferences, words: List<String>) {
            prefs.edit().putString(KEYWORDS, JSONArray(words.map(String::trim).filter(String::isNotEmpty).distinct()).toString()).apply()
        }
    }
}
