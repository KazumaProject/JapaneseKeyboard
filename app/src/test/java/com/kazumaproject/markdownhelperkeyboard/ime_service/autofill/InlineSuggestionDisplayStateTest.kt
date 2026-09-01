package com.kazumaproject.markdownhelperkeyboard.ime_service.autofill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineSuggestionDisplayStateTest {

    @Test
    fun newlyAvailableSuggestionsUseInlineSurface() {
        val state = InlineSuggestionDisplayState()

        state.updateAvailability(true)

        assertTrue(state.hasSuggestions)
        assertEquals(InlineSuggestionSurface.Inline, state.surface)
    }

    @Test
    fun availableSuggestionsCanToggleBetweenNativeAndInlineSurfaces() {
        val state = InlineSuggestionDisplayState().apply {
            updateAvailability(true)
        }

        assertTrue(state.toggleSurface())
        assertEquals(InlineSuggestionSurface.Native, state.surface)
        assertTrue(state.toggleSurface())
        assertEquals(InlineSuggestionSurface.Inline, state.surface)
    }

    @Test
    fun republishingAvailableSuggestionsPreservesTheSelectedSurface() {
        val state = InlineSuggestionDisplayState().apply {
            updateAvailability(true)
            toggleSurface()
        }

        state.updateAvailability(true)

        assertEquals(InlineSuggestionSurface.Native, state.surface)
    }

    @Test
    fun clearingSuggestionsResetsTheNextResponseToInlineSurface() {
        val state = InlineSuggestionDisplayState().apply {
            updateAvailability(true)
            toggleSurface()
        }

        state.updateAvailability(false)

        assertFalse(state.hasSuggestions)
        assertFalse(state.toggleSurface())
        state.updateAvailability(true)
        assertEquals(InlineSuggestionSurface.Inline, state.surface)
    }
}
