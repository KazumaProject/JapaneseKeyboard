package com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry

import android.content.SharedPreferences
import com.kazumaproject.core.ui.geometry.KeyboardCurveHand
import com.kazumaproject.core.ui.geometry.KeyboardGeometry

enum class GeometryKeyboard { QWERTY, TENKEY, SUMIRE }
enum class GeometryCandidatePlacement { TOP, LEFT, RIGHT, CENTER }

data class KeyboardGeometryPreference(
    val geometry: KeyboardGeometry = KeyboardGeometry(),
    val candidates: GeometryCandidatePlacement = GeometryCandidatePlacement.TOP,
)

/** Separate keys per keyboard and screen orientation; legacy backups use default values. */
class KeyboardGeometryPreferences(private val preferences: SharedPreferences) {
    private fun prefix(keyboard: GeometryKeyboard, landscape: Boolean): String =
        "keyboard_geometry_${keyboard.name.lowercase()}_${if (landscape) "landscape" else "portrait"}"

    fun read(keyboard: GeometryKeyboard, landscape: Boolean): KeyboardGeometryPreference {
        val p = prefix(keyboard, landscape)
        fun int(name: String, default: Int) = runCatching { preferences.getInt("${p}_$name", default) }.getOrDefault(default)
        fun string(name: String, default: String) = runCatching { preferences.getString("${p}_$name", default) }.getOrNull() ?: default
        val hand = runCatching { KeyboardCurveHand.valueOf(string("hand", "RIGHT")) }.getOrDefault(KeyboardCurveHand.RIGHT)
        val placement = runCatching { GeometryCandidatePlacement.valueOf(string("candidates", "TOP")) }.getOrDefault(GeometryCandidatePlacement.TOP)
        val allowed = when (keyboard) {
            GeometryKeyboard.QWERTY -> placement == GeometryCandidatePlacement.CENTER || placement == GeometryCandidatePlacement.TOP
            else -> placement != GeometryCandidatePlacement.CENTER
        }
        return KeyboardGeometryPreference(
            geometry = KeyboardGeometry(
                splitPercent = if (keyboard == GeometryKeyboard.QWERTY) int("split", 0) else 0,
                curveDegrees = int("curve", 0),
                hand = hand,
                autoHeight = runCatching { preferences.getBoolean("${p}_auto_height", true) }.getOrDefault(true),
            ).normalized(),
            candidates = if (allowed) placement else GeometryCandidatePlacement.TOP,
        )
    }

    fun save(keyboard: GeometryKeyboard, landscape: Boolean, preference: KeyboardGeometryPreference) {
        val p = prefix(keyboard, landscape)
        val value = preference.geometry.normalized()
        val placement = preference.candidates.takeIf {
            it == GeometryCandidatePlacement.TOP ||
                (keyboard == GeometryKeyboard.QWERTY && it == GeometryCandidatePlacement.CENTER) ||
                (keyboard != GeometryKeyboard.QWERTY && it in setOf(GeometryCandidatePlacement.LEFT, GeometryCandidatePlacement.RIGHT))
        } ?: GeometryCandidatePlacement.TOP
        preferences.edit().putInt("${p}_split", if (keyboard == GeometryKeyboard.QWERTY) value.splitPercent else 0)
            .putInt("${p}_curve", value.curveDegrees).putString("${p}_hand", value.hand.name)
            .putBoolean("${p}_auto_height", value.autoHeight).putString("${p}_candidates", placement.name).apply()
    }

    fun reset(keyboard: GeometryKeyboard, landscape: Boolean) = save(keyboard, landscape, KeyboardGeometryPreference())
}
