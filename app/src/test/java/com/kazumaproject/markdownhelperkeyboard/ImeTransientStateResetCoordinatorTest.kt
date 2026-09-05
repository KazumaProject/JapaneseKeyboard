package com.kazumaproject.markdownhelperkeyboard

import com.kazumaproject.markdownhelperkeyboard.ime_service.ImeTransientStateResetCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeTransientStateResetCoordinatorTest {
    @Test
    fun resetRunsInTheContractedOrderAndReturnsCurrentGeneration() {
        val coordinator = ImeTransientStateResetCoordinator()
        val calls = mutableListOf<String>()

        val generation = coordinator.reset(
            ImeTransientStateResetCoordinator.Actions(
                cancelAsyncWork = { calls += "cancel-async" },
                resetGestureState = { calls += "reset-gesture" },
                clearTransientState = { calls += "clear-transient" },
                clearViewsAndEffects = { calls += "clear-views" },
                clearInputConnection = { calls += "clear-input-connection" },
            ),
        )

        assertEquals(1L, generation)
        assertEquals(
            listOf(
                "cancel-async",
                "reset-gesture",
                "clear-transient",
                "clear-views",
                "clear-input-connection",
            ),
            calls,
        )
        assertTrue(coordinator.isCurrent(generation))
    }

    @Test
    fun aNewResetInvalidatesThePreviousGeneration() {
        val coordinator = ImeTransientStateResetCoordinator()
        val firstGeneration = coordinator.reset(noOpActions())
        val secondGeneration = coordinator.reset(noOpActions())

        assertEquals(2L, secondGeneration)
        assertFalse(coordinator.isCurrent(firstGeneration))
        assertTrue(coordinator.isCurrent(secondGeneration))
    }

    private fun noOpActions() = ImeTransientStateResetCoordinator.Actions(
        cancelAsyncWork = {},
        resetGestureState = {},
        clearTransientState = {},
        clearViewsAndEffects = {},
        clearInputConnection = {},
    )
}
