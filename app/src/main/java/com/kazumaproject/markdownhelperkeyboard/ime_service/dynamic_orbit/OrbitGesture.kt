package com.kazumaproject.markdownhelperkeyboard.ime_service.dynamic_orbit

import kotlin.math.*

/** Distances are dp, angles clockwise from screen-up. No clock participates in recognition. */
internal object OrbitGeometry {
    const val ROW = 24f
    const val NEUTRAL = 14f
    const val VOWEL = 44f
    const val ROW_RETURN = 36f
    const val COMMIT = 72f
    const val COMMIT_REARM = 64f
    const val CLEARANCE = 80f
    const val MIN_WIDTH = 240
    const val MIN_HEIGHT = 256
    const val COMMAND_HEIGHT = 48
    val rows = listOf("あいうえお", "かきくけこ", "さしすせそ", "たちつてと", "なにぬねの",
        "はひふへほ", "まみむめも", "や ゆ よ", "らりるれろ", "わをんー ")
    val vowelAngles = listOf(225f, 315f, 0f, 45f, 135f)
    fun letter(row: Int?, vowel: Int?): Char? =
        if (row == null || vowel == null) null else rows[row][vowel].takeUnless { it == ' ' }
    fun point(radius: Float, angle: Float): OrbitPoint {
        val radians = Math.toRadians(angle.toDouble())
        return OrbitPoint((radius * sin(radians)).toFloat(), (-radius * cos(radians)).toFloat())
    }
}

internal data class OrbitPoint(val x: Float, val y: Float) {
    val radius: Float get() = hypot(x, y)
    val angle: Float get() = ((Math.toDegrees(atan2(x.toDouble(), -y.toDouble())) + 360) % 360).toFloat()
}
internal enum class OrbitPhase { IDLE, NEUTRAL, ROW, VOWEL, RETURN }
internal data class OrbitSnapshot(
    val phase: OrbitPhase = OrbitPhase.IDLE,
    val row: Int? = null,
    val vowel: Int? = null,
    val point: OrbitPoint = OrbitPoint(0f, 0f),
    val lastLetter: Char? = null,
) {
    val preview: Char? get() = OrbitGeometry.letter(row, vowel)
}

/** The stroke origin never moves. A committed character requires a neutral return to rearm. */
internal class OrbitGesture {
    var snapshot = OrbitSnapshot()
        private set
    private var commitArmed = true

    fun start() { snapshot = OrbitSnapshot(OrbitPhase.NEUTRAL); commitArmed = true }
    fun cancel() { snapshot = OrbitSnapshot(); commitArmed = true }

    fun move(point: OrbitPoint): List<Char> {
        if (snapshot.phase == OrbitPhase.IDLE || !point.x.isFinite() || !point.y.isFinite()) return emptyList()
        val from = snapshot.point
        // Subdivide geometry, not time: long/coalesced moves cross the same gates as short moves.
        val steps = ceil(hypot(point.x - from.x, point.y - from.y)).toInt().coerceAtLeast(1)
        val output = mutableListOf<Char>()
        for (step in 1..steps) {
            val fraction = step.toFloat() / steps
            advance(OrbitPoint(from.x + (point.x - from.x) * fraction,
                from.y + (point.y - from.y) * fraction))?.let(output::add)
        }
        return output
    }

    private fun advance(point: OrbitPoint): Char? {
        val radius = point.radius
        val previousRadius = snapshot.point.radius
        snapshot = snapshot.copy(point = point)
        when (snapshot.phase) {
            OrbitPhase.IDLE -> return null
            OrbitPhase.RETURN -> {
                if (radius <= OrbitGeometry.NEUTRAL) snapshot = OrbitSnapshot(OrbitPhase.NEUTRAL, point = point)
                return null
            }
            OrbitPhase.NEUTRAL -> {
                if (radius < OrbitGeometry.ROW) return null
                snapshot = snapshot.copy(phase = OrbitPhase.ROW, row = selectRow(point.angle, null))
            }
            else -> Unit
        }
        if (snapshot.phase == OrbitPhase.ROW) {
            if (radius <= OrbitGeometry.NEUTRAL) {
                snapshot = OrbitSnapshot(OrbitPhase.NEUTRAL, point = point)
                return null
            }
            snapshot = snapshot.copy(row = selectRow(point.angle, snapshot.row), vowel = null)
            if (radius >= OrbitGeometry.VOWEL) {
                snapshot = snapshot.copy(phase = OrbitPhase.VOWEL)
                commitArmed = true
            }
        }
        if (snapshot.phase == OrbitPhase.VOWEL) {
            if (radius <= OrbitGeometry.ROW_RETURN) {
                snapshot = snapshot.copy(phase = OrbitPhase.ROW, vowel = null)
                return null
            }
            snapshot = snapshot.copy(vowel = selectVowel(point.angle, snapshot.vowel))
            if (radius <= OrbitGeometry.COMMIT_REARM) commitArmed = true
            if (commitArmed && previousRadius < OrbitGeometry.COMMIT && radius >= OrbitGeometry.COMMIT) {
                commitArmed = false
                val letter = snapshot.preview ?: return null
                snapshot = snapshot.copy(phase = OrbitPhase.RETURN, lastLetter = letter)
                return letter
            }
        }
        return null
    }

    private fun selectRow(angle: Float, previous: Int?): Int {
        val nearest = ((angle + 18f) / 36f).toInt() % 10
        return if (previous != null && angularDistance(angle, previous * 36f) <= 22f) previous else nearest
    }

    private fun selectVowel(angle: Float, previous: Int?): Int {
        val angles = OrbitGeometry.vowelAngles
        val nearest = angles.indices.minBy { angularDistance(angle, angles[it]) }
        if (previous == null || previous == nearest) return nearest
        // The five directions are not equally spaced. Compare distances to their actual bisector.
        return if (angularDistance(angle, angles[previous]) - angularDistance(angle, angles[nearest]) <= 10f)
            previous else nearest
    }

    private fun angularDistance(a: Float, b: Float): Float = abs((a - b + 540f) % 360f - 180f)
}
