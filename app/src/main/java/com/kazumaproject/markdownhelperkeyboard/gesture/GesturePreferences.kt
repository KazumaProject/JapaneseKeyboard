package com.kazumaproject.markdownhelperkeyboard.gesture

import android.content.Context
import androidx.preference.PreferenceManager
import com.kazumaproject.gesture_keyboard.*

class GesturePreferences(context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val ja = localizedGestureContext(context).resources.configuration.locales[0].language == "ja"
    private fun <T> read(key: String, default: T, decode: (String) -> T): T =
        runCatching { prefs.getString(key, null)?.let(decode) ?: default }.getOrDefault(default)
    fun customSets(): List<GestureSet> = read(SETS, emptyList(), GestureCodec::decodeSets)
    fun sets() = GesturePresets.sets(ja) + customSets()
    fun layout() = read(LAYOUT, GestureControlLayout(), GestureCodec::decodeLayout)
    fun session(): GestureSessionState {
        val state = read(SESSION, GestureSessionState(), GestureCodec::decodeSession)
        val ids = sets().map { it.id }
        return state.copy(leftSetId = state.leftSetId.takeIf { it in ids } ?: GesturePresets.KANA,
            rightSetId = state.rightSetId.takeIf { it in ids } ?: GesturePresets.DIGITS)
    }
    fun showGuide() = runCatching { prefs.getBoolean("gesture_show_guide", true) }.getOrDefault(true)
    fun maxDistance() = runCatching { prefs.getInt("gesture_recognition_tolerance", 14) }.getOrDefault(14).coerceIn(6, 25) / 100f
    fun invalidData(): Boolean = listOf(
        SETS to { json: String -> GestureCodec.decodeSets(json); Unit },
        LAYOUT to { json: String -> GestureCodec.decodeLayout(json); Unit },
        SESSION to { json: String -> GestureCodec.decodeSession(json); Unit }
    ).any { (key, decode) -> prefs.contains(key) && runCatching { decode(prefs.getString(key, "")!!) }.isFailure }
    private fun ensureReadable(key: String, decode: (String) -> Any) {
        if (prefs.contains(key)) require(runCatching { decode(prefs.getString(key, "")!!) }.isSuccess) { if (ja) "保存データが不正です。バックアップから復元してください" else "Invalid saved data. Restore a backup." }
    }
    fun saveSet(set: GestureSet) {
        ensureReadable(SETS, GestureCodec::decodeSets)
        require(!set.builtIn && !set.id.startsWith("builtin_")); set.validate()
        val next = customSets().filterNot { it.id == set.id } + set
        require(next.size <= 64)
        prefs.edit().putString(SETS, GestureCodec.encodeSets(next)).apply()
    }
    fun deleteSet(id: String) {
        ensureReadable(SETS, GestureCodec::decodeSets)
        require(!id.startsWith("builtin_"))
        val state = session()
        prefs.edit().putString(SETS, GestureCodec.encodeSets(customSets().filterNot { it.id == id }))
            .putString(SESSION, GestureCodec.encodeSession(state.copy(
                leftSetId = if (state.leftSetId == id) GesturePresets.KANA else state.leftSetId,
                rightSetId = if (state.rightSetId == id) GesturePresets.DIGITS else state.rightSetId))).apply()
    }
    fun saveSession(state: GestureSessionState) {
        ensureReadable(SESSION, GestureCodec::decodeSession)
        require(listOf(state.leftSetId, state.rightSetId).all { id -> sets().any { it.id == id } })
        prefs.edit().putString(SESSION, GestureCodec.encodeSession(state)).apply()
    }
    fun saveLayout(layout: GestureControlLayout) {
        ensureReadable(LAYOUT, GestureCodec::decodeLayout)
        prefs.edit().putString(LAYOUT, GestureCodec.encodeLayout(layout)).apply()
    }
    companion object {
        const val SETS = "gesture_custom_sets_v1"
        const val LAYOUT = "gesture_controls_v1"
        const val SESSION = "gesture_session_v1"
    }
}
