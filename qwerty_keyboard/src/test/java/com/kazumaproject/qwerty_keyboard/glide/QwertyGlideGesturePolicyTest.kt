package com.kazumaproject.qwerty_keyboard.glide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QwertyGlideGesturePolicyTest {
    @Test
    fun shortTapDoesNotStartGlide() {
        assertFalse(
            QwertyGlideGesturePolicy.shouldStart(
                pointCount = 2,
                directDistance = 8f,
                elapsedMillis = 30L,
                distinctLetterKeysNearTrail = 1,
                minMoveDistance = 24f,
                fastMoveDistance = 36f,
                minElapsedMillis = 45L
            )
        )
    }

    @Test
    fun deliberateMovementAcrossLettersStartsGlide() {
        assertTrue(
            QwertyGlideGesturePolicy.shouldStart(
                pointCount = 5,
                directDistance = 30f,
                elapsedMillis = 70L,
                distinctLetterKeysNearTrail = 2,
                minMoveDistance = 24f,
                fastMoveDistance = 36f,
                minElapsedMillis = 45L
            )
        )
    }

    @Test
    fun fastMovementCanStartBeforeMinimumElapsedTime() {
        assertTrue(
            QwertyGlideGesturePolicy.shouldStart(
                pointCount = 4,
                directDistance = 48f,
                elapsedMillis = 24L,
                distinctLetterKeysNearTrail = 2,
                minMoveDistance = 24f,
                fastMoveDistance = 36f,
                minElapsedMillis = 45L
            )
        )
    }

    @Test
    fun movementWithinSingleKeyDoesNotStartGlide() {
        assertFalse(
            QwertyGlideGesturePolicy.shouldStart(
                pointCount = 4,
                directDistance = 50f,
                elapsedMillis = 80L,
                distinctLetterKeysNearTrail = 1,
                minMoveDistance = 24f,
                fastMoveDistance = 36f,
                minElapsedMillis = 45L
            )
        )
    }
}
