package com.kazumaproject.custom_keyboard.controller

import com.kazumaproject.core.domain.flick.TfbiDiagonalRecognitionMode
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class TfbiDiagonalGuardTest {
    @Test
    fun stableModeKeepsCardinalWhenSecondStageOnlyDriftsSideways() {
        assertEquals(
            TfbiFlickDirection.RIGHT,
            resolve(TfbiFlickDirection.RIGHT, TfbiFlickDirection.DOWN_RIGHT, 80f, 19f)
        )
        assertEquals(
            TfbiFlickDirection.UP,
            resolve(TfbiFlickDirection.UP, TfbiFlickDirection.UP_LEFT, -19f, -80f)
        )
        assertEquals(
            TfbiFlickDirection.LEFT,
            resolve(TfbiFlickDirection.LEFT, TfbiFlickDirection.UP_LEFT, -80f, -19f)
        )
        assertEquals(
            TfbiFlickDirection.DOWN,
            resolve(TfbiFlickDirection.DOWN, TfbiFlickDirection.DOWN_RIGHT, 19f, 80f)
        )
    }

    @Test
    fun stableModeAcceptsClearTurnAtThreshold() {
        assertEquals(
            TfbiFlickDirection.DOWN_RIGHT,
            resolve(TfbiFlickDirection.RIGHT, TfbiFlickDirection.DOWN_RIGHT, 80f, 20f)
        )
        assertEquals(
            TfbiFlickDirection.UP_LEFT,
            resolve(TfbiFlickDirection.UP, TfbiFlickDirection.UP_LEFT, -20f, -80f)
        )
    }

    @Test
    fun legacyAndUnpairedDirectionsKeepOriginalSelection() {
        assertEquals(
            TfbiFlickDirection.DOWN_RIGHT,
            resolve(
                TfbiFlickDirection.RIGHT, TfbiFlickDirection.DOWN_RIGHT, 80f, 1f,
                TfbiDiagonalRecognitionMode.LEGACY
            )
        )
        assertEquals(
            TfbiFlickDirection.DOWN_RIGHT,
            guardTfbiSecondStageDiagonal(
                TfbiFlickDirection.DOWN_RIGHT, TfbiFlickDirection.RIGHT,
                80f, 1f, 20f, setOf(TfbiFlickDirection.DOWN_RIGHT),
                TfbiDiagonalRecognitionMode.STABLE
            )
        )
    }

    private fun resolve(
        entry: TfbiFlickDirection,
        candidate: TfbiFlickDirection,
        dx: Float,
        dy: Float,
        mode: TfbiDiagonalRecognitionMode = TfbiDiagonalRecognitionMode.STABLE,
    ) = guardTfbiSecondStageDiagonal(
        candidate, entry, dx, dy, 20f, setOf(entry, candidate), mode
    )
}
