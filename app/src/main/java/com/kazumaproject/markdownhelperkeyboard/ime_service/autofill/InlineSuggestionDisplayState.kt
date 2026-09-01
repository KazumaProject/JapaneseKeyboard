package com.kazumaproject.markdownhelperkeyboard.ime_service.autofill

internal enum class InlineSuggestionSurface {
    Inline,
    Native,
}

/** Keeps the chosen candidate-strip surface while the current inline response is available. */
internal class InlineSuggestionDisplayState {
    var hasSuggestions: Boolean = false
        private set

    var surface: InlineSuggestionSurface = InlineSuggestionSurface.Inline
        private set

    fun updateAvailability(available: Boolean) {
        if (!available) {
            hasSuggestions = false
            surface = InlineSuggestionSurface.Inline
            return
        }

        // A newly available response keeps the existing inline-first behavior. Re-publishing the
        // same response for a different host must not undo a user-selected native surface.
        if (!hasSuggestions) {
            surface = InlineSuggestionSurface.Inline
        }
        hasSuggestions = true
    }

    fun toggleSurface(): Boolean {
        if (!hasSuggestions) return false
        surface = when (surface) {
            InlineSuggestionSurface.Inline -> InlineSuggestionSurface.Native
            InlineSuggestionSurface.Native -> InlineSuggestionSurface.Inline
        }
        return true
    }
}
