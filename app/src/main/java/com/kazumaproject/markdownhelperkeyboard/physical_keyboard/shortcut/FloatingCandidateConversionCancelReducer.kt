package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut

import androidx.recyclerview.widget.RecyclerView

data class FloatingCandidateConversionState(
    val inputString: String,
    val isHenkan: Boolean,
    val henkanPressedWithBunsetsuDetect: Boolean,
    val stringInTail: String,
    val currentHighlightIndex: Int,
    val hasConversionSuggestions: Boolean
)

object FloatingCandidateConversionCancelReducer {
    fun cancel(
        state: FloatingCandidateConversionState,
        insertString: String
    ): FloatingCandidateConversionState {
        return state.copy(
            inputString = insertString,
            isHenkan = false,
            henkanPressedWithBunsetsuDetect = false,
            stringInTail = "",
            currentHighlightIndex = RecyclerView.NO_POSITION,
            hasConversionSuggestions = false
        )
    }
}
