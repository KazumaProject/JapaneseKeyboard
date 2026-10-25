package com.kazumaproject.gesture_keyboard

import org.junit.Assert.*
import org.junit.Test

class GestureSessionStateTest {
    @Test fun allCountsAreNonOverlappingAndKeepMapAssignments() {
        var state = GestureSessionState().withSet("pad_1", "personal")
        for (count in listOf(1, 2, 3, 4, 2)) {
            state = state.withCount(count)
            state.validate()
            assertEquals(count, state.pads.size)
            assertEquals("personal", state.pads.first().setId)
        }
        assertEquals(GesturePresets.ROMAJI, GestureSessionState().withCount(4).pads[3].setId)
    }
    @Test fun touchingEdgesIsAllowedButOverlapOutOfBoundsAndTooSmallAreRejected() {
        val state = GestureSessionState()
        state.validate()
        for (bounds in listOf(GestureBounds(.1f, 0f, .5f, 1f), GestureBounds(-.1f, 0f, .5f, 1f),
            GestureBounds(0f, 0f, .19f, 1f), GestureBounds(0f, 0f, .5f, .24f), GestureBounds(Float.NaN, 0f, .5f, 1f))) {
            assertThrows(IllegalArgumentException::class.java) { state.update("pad_1") { it.copy(bounds = bounds) }.validate() }
        }
    }
    @Test fun movingShrinkingAndRemovingTheActiveAreaPreservesOtherAreasAndRepairsFocus() {
        val state = GestureSessionState().withCount(4).copy(activePadId = "pad_4")
        val edited = state.update("pad_1") { it.copy(bounds = GestureBounds(.1f, .1f, .2f, .25f)) }
        edited.validate()
        assertEquals(state.pads.drop(1), edited.pads.drop(1))
        assertEquals("pad_1", edited.withCount(2).activePadId)
    }
    @Test fun mapsHaveAnExplicitRomajiFlagAndShareTheActualAlphabetSamples() {
        val maps = GesturePresets.sets()
        val latin = maps.first { it.id == GesturePresets.LATIN }
        val romaji = maps.first { it.id == GesturePresets.ROMAJI }
        assertFalse(latin.isRomaji)
        assertTrue(romaji.isRomaji)
        assertEquals(latin.entries, romaji.entries)
    }
}
