package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.QuickActionsState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateStripLayoutPolicyTest {

    @Test
    fun gemmaActionsUseLinearHorizontalLayout() {
        assertTrue(
            CandidateStripLayoutPolicy.shouldUseLinearHorizontalLayout(
                CandidateStripContent.GemmaActions(
                    actions = listOf(candidate("Translate")),
                    showShortcutEntry = false
                )
            )
        )
    }

    @Test
    fun visibleZeroQuerySuggestionsUseLinearHorizontalLayout() {
        assertTrue(
            CandidateStripLayoutPolicy.shouldUseLinearHorizontalLayout(
                CandidateStripContent.ZeroQuerySuggestions(
                    candidates = listOf(candidate("候補1"), candidate("候補2"))
                )
            )
        )
    }

    @Test
    fun normalCandidatesStillUseConfiguredCandidateRows() {
        assertFalse(
            CandidateStripLayoutPolicy.shouldUseLinearHorizontalLayout(
                CandidateStripContent.Candidates(
                    candidates = listOf(candidate("候補1"), candidate("候補2"))
                )
            )
        )
    }

    @Test
    fun hiddenZeroQueryToggleUsesLinearHorizontalLayout() {
        assertTrue(
            CandidateStripLayoutPolicy.shouldUseLinearHorizontalLayout(
                CandidateStripContent.EmptyState(
                    showShortcutEntry = false,
                    quickActions = QuickActionsState(
                        incognitoVisible = false,
                        undoEnabled = false,
                        redoEnabled = false,
                        reconvertEnabled = false,
                        undoText = "",
                        redoText = "",
                    ),
                    clipboardPreview = null,
                    shortcutItems = emptyList(),
                    showIntegratedShortcuts = false,
                    showZeroQueryToggle = true,
                )
            )
        )
    }

    private fun candidate(string: String): Candidate {
        return Candidate(
            string = string,
            type = 1.toByte(),
            length = string.length.toUByte(),
            score = 0,
            yomi = string
        )
    }
}
