package com.kazumaproject.core.domain.flick

import org.junit.Assert.*
import org.junit.Test

class FlickEvidenceModelTest {
    private val ka = FlickEvidenceKey(0f, 0f, 100f, 100f, mapOf(
        FlickDirection.Tap to 'か', FlickDirection.Left to 'き', FlickDirection.Top to 'く',
        FlickDirection.Right to 'け', FlickDirection.Bottom to 'こ',
    ))

    @Test fun shortFlickSupportsTapButLongFlickDoesNot() {
        fun sample(dy: Float) = FlickEvidenceModel.create('く', 50f, 50f, 0f, dy,
            20f, FlickThresholdShape.Radial, listOf(ka))!!
        assertTrue(sample(-20f).costFactor('か') < 0.85f)
        assertTrue(sample(-80f).costFactor('か') > 1.5f)
    }

    @Test fun diagonalBoundarySupportsAdjacentDirectionAndRejectsOppositeDirection() {
        val sample = FlickEvidenceModel.create('く', 50f, 50f, -20f, -21f,
            20f, FlickThresholdShape.Radial, listOf(ka))!!
        assertTrue(sample.costFactor('き') < 0.85f)
        assertTrue(sample.costFactor('け') > 1.5f)
    }

    @Test fun keyBoundarySupportsNeighborMoreThanCenterTouch() {
        val neighbor = ka.copy(left = 100f, characters = mapOf(FlickDirection.Tap to 'さ'))
        fun sample(x: Float) = FlickEvidenceModel.create('か', x, 50f, 0f, 0f,
            20f, FlickThresholdShape.Rectangular, listOf(ka, neighbor))!!
        assertTrue(sample(99f).costFactor('さ') < 0.85f)
        assertTrue(sample(50f).costFactor('さ') > 1.5f)
    }

    @Test fun invalidCoordinatesDoNotCreateEvidence() {
        assertNull(FlickEvidenceModel.create('か', Float.NaN, 50f, 0f, 0f,
            20f, FlickThresholdShape.Radial, listOf(ka)))
    }
}
