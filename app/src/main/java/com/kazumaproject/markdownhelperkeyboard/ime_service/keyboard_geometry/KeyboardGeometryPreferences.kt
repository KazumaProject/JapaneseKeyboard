package com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_geometry

import android.content.SharedPreferences
import com.kazumaproject.core.ui.geometry.KeyboardCurveDirection
import com.kazumaproject.core.ui.geometry.KeyboardCurveHand
import com.kazumaproject.core.ui.geometry.KeyboardGeometry
import kotlin.math.roundToInt

enum class GeometryKeyboard { QWERTY, TENKEY, SUMIRE }
enum class GeometryCandidatePlacement { TOP, LEFT, RIGHT, CENTER }

data class KeyboardGeometryPreference(
    val geometry: KeyboardGeometry = KeyboardGeometry(),
    val candidates: GeometryCandidatePlacement = GeometryCandidatePlacement.TOP,
    val sideCandidateWidthPercent: Int = SideCandidateSizing.DEFAULT_PERCENT,
    val sideCandidateVerticalText: Boolean = false,
    val sideCandidateRightToLeft: Boolean = false,
    val sideCandidateWrapLongText: Boolean = false,
    val sideCandidateSpacingDp: Int = SideCandidateSizing.DEFAULT_SPACING_DP,
    val centerCandidateSpacingDp: Int = 0,
)

/** Size rules only; settings previews and IME views remain separate. */
object SideCandidateSizing {
    const val MIN_PERCENT = 15
    const val MAX_PERCENT = 50
    const val DEFAULT_PERCENT = 25
    const val DEFAULT_SPACING_DP = 4
    const val MAX_SPACING_DP = 32

    fun width(surfaceWidth: Int, density: Float, percent: Int): Int {
        if (surfaceWidth < (256f * density).toInt()) return 0
        val minimum = (64f * density).toInt().coerceAtLeast(1)
        val maximum = (surfaceWidth - (192f * density).toInt()).coerceAtLeast(minimum)
        val requested = (surfaceWidth.toLong() * percent.coerceIn(MIN_PERCENT, MAX_PERCENT) / 100).toInt()
        return requested.coerceIn(minimum, maximum)
    }

    /** Leave at least 64 dp per candidate, or more for unusually large text. */
    fun columnCount(candidateWidth: Int, density: Float, textSizePx: Float): Int {
        val minimumColumnWidth = minimumColumnWidth(density, textSizePx)
        val usableWidth = (candidateWidth - 2 * horizontalInset(candidateWidth, density)).coerceAtLeast(0)
        return (usableWidth / minimumColumnWidth).toInt().coerceAtLeast(1)
    }

    /** Keep the default gaps within the panel and let larger gaps move columns apart. */
    fun columnWidth(candidateWidth: Int, density: Float, textSizePx: Float): Int {
        val usableWidth = (candidateWidth - 2 * horizontalInset(candidateWidth, density)).coerceAtLeast(0)
        val columns = columnCount(candidateWidth, density, textSizePx)
        val defaultGap = (DEFAULT_SPACING_DP * density).roundToInt()
        return minOf(minimumColumnWidth(density, textSizePx).toInt(), usableWidth / columns - defaultGap).coerceAtLeast(1)
    }

    private fun minimumColumnWidth(density: Float, textSizePx: Float): Float =
        maxOf(64f * density, textSizePx * 1.2f + 24f * density, 1f)

    /** Preserve at least a 48 dp target when the candidate area is very narrow. */
    fun horizontalInset(candidateWidth: Int, density: Float): Int =
        minOf((8f * density).toInt(), ((candidateWidth - (48f * density).toInt()) / 2).coerceAtLeast(0))
}

/** Separate keys per keyboard and screen orientation; legacy backups use default values. */
class KeyboardGeometryPreferences(private val preferences: SharedPreferences) {
    private fun prefix(keyboard: GeometryKeyboard, landscape: Boolean): String =
        "keyboard_geometry_${keyboard.name.lowercase()}_${if (landscape) "landscape" else "portrait"}"

    fun read(keyboard: GeometryKeyboard, landscape: Boolean): KeyboardGeometryPreference {
        val p = prefix(keyboard, landscape)
        fun int(name: String, default: Int) = runCatching { preferences.getInt("${p}_$name", default) }.getOrDefault(default)
        fun string(name: String, default: String) = runCatching { preferences.getString("${p}_$name", default) }.getOrNull() ?: default
        val hand = runCatching { KeyboardCurveHand.valueOf(string("hand", "RIGHT")) }.getOrDefault(KeyboardCurveHand.RIGHT)
        val direction = runCatching { KeyboardCurveDirection.valueOf(string("curve_direction", "DOWN")) }.getOrDefault(KeyboardCurveDirection.DOWN)
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
                curveDirection = direction,
            ).normalized(),
            candidates = if (allowed) placement else GeometryCandidatePlacement.TOP,
            sideCandidateWidthPercent = int("candidate_width_percent", SideCandidateSizing.DEFAULT_PERCENT)
                .coerceIn(SideCandidateSizing.MIN_PERCENT, SideCandidateSizing.MAX_PERCENT),
            sideCandidateVerticalText = runCatching { preferences.getBoolean("${p}_candidate_vertical_text", false) }.getOrDefault(false),
            sideCandidateRightToLeft = runCatching { preferences.getBoolean("${p}_candidate_right_to_left", false) }.getOrDefault(false),
            sideCandidateWrapLongText = runCatching { preferences.getBoolean("${p}_candidate_wrap_long_text", false) }.getOrDefault(false),
            sideCandidateSpacingDp = int("candidate_spacing_dp", SideCandidateSizing.DEFAULT_SPACING_DP)
                .coerceIn(0, SideCandidateSizing.MAX_SPACING_DP),
            centerCandidateSpacingDp = int("center_candidate_spacing_dp", 0)
                .coerceIn(0, SideCandidateSizing.MAX_SPACING_DP),
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
            .putString("${p}_curve_direction", value.curveDirection.name)
            .putBoolean("${p}_auto_height", value.autoHeight).putString("${p}_candidates", placement.name)
            .putInt("${p}_candidate_width_percent", preference.sideCandidateWidthPercent.coerceIn(SideCandidateSizing.MIN_PERCENT, SideCandidateSizing.MAX_PERCENT))
            .putBoolean("${p}_candidate_vertical_text", preference.sideCandidateVerticalText)
            .putBoolean("${p}_candidate_right_to_left", preference.sideCandidateRightToLeft)
            .putBoolean("${p}_candidate_wrap_long_text", preference.sideCandidateWrapLongText)
            .putInt("${p}_candidate_spacing_dp", preference.sideCandidateSpacingDp.coerceIn(0, SideCandidateSizing.MAX_SPACING_DP))
            .putInt("${p}_center_candidate_spacing_dp", preference.centerCandidateSpacingDp.coerceIn(0, SideCandidateSizing.MAX_SPACING_DP)).apply()
    }

    fun reset(keyboard: GeometryKeyboard, landscape: Boolean) = save(keyboard, landscape, KeyboardGeometryPreference())
}
