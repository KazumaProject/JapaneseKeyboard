package com.kazumaproject.qwerty_keyboard.glide

object QwertyGlideGesturePolicy {
    fun shouldStart(
        pointCount: Int,
        directDistance: Float,
        elapsedMillis: Long,
        distinctLetterKeysNearTrail: Int,
        minMoveDistance: Float,
        fastMoveDistance: Float,
        minElapsedMillis: Long
    ): Boolean {
        if (pointCount < 3) return false
        if (distinctLetterKeysNearTrail < 2) return false
        val fastMove = directDistance >= fastMoveDistance
        val deliberateMove = directDistance >= minMoveDistance && elapsedMillis >= minElapsedMillis
        return fastMove || deliberateMove
    }
}
