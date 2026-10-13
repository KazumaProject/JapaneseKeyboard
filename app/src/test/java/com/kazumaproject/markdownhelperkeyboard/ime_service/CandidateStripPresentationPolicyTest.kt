package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateStripPresentationPolicyTest {

    @Test
    fun shortcutToolbarVisibilityFalseDisablesIndependentAndIntegratedShortcuts() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(shortcutToolbarVisible = false)
        )

        assertFalse(presentation.showIndependentShortcutToolbar)
        assertFalse(presentation.reserveIndependentShortcutToolbarSpace)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun shortcutToolbarVisibleAndIntegrationOffUsesIndependentToolbar() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(shortcutToolbarIntegratedInSuggestion = false)
        )

        assertTrue(presentation.showIndependentShortcutToolbar)
        assertFalse(presentation.reserveIndependentShortcutToolbarSpace)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun integratedOnShowsShortcutItemsForNormalEmptyState() {
        val presentation = CandidateStripPresentationPolicy.resolve(baseState())

        assertFalse(presentation.showIndependentShortcutToolbar)
        assertTrue(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun nonEmptyInputDisablesIntegratedShortcut() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(inputStringEmpty = false)
        )

        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun nonEmptyTailDisablesIntegratedShortcut() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(tailEmpty = false)
        )

        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun integratedOnClipboardPreviewShowsShortcutEntryOnly() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(clipboardPreviewShown = true)
        )

        assertFalse(presentation.showIndependentShortcutToolbar)
        assertFalse(presentation.reserveIndependentShortcutToolbarSpace)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertTrue(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun integratedOnSelectionActionsShowShortcutEntryOnly() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(selectionActionsShown = true, suggestionsEmpty = false)
        )

        assertFalse(presentation.showIndependentShortcutToolbar)
        assertFalse(presentation.reserveIndependentShortcutToolbarSpace)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertTrue(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun integratedOffClipboardPreviewDoesNotShowShortcutEntry() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(
                shortcutToolbarIntegratedInSuggestion = false,
                clipboardPreviewShown = true
            )
        )

        assertTrue(presentation.showIndependentShortcutToolbar)
        assertFalse(presentation.reserveIndependentShortcutToolbarSpace)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun integratedOffSelectionActionsDoNotShowShortcutEntry() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(
                shortcutToolbarIntegratedInSuggestion = false,
                selectionActionsShown = true,
                suggestionsEmpty = false
            )
        )

        assertTrue(presentation.showIndependentShortcutToolbar)
        assertFalse(presentation.reserveIndependentShortcutToolbarSpace)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun customLayoutPickerDisablesIntegratedShortcut() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(customLayoutPickerShown = true, clipboardPreviewShown = true)
        )

        assertFalse(presentation.showIndependentShortcutToolbar)
        assertFalse(presentation.reserveIndependentShortcutToolbarSpace)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun symbolKeyboardDisablesShortcuts() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(symbolKeyboardShown = true)
        )

        assertFalse(presentation.showIndependentShortcutToolbar)
        assertFalse(presentation.reserveIndependentShortcutToolbarSpace)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun regularSuggestionsDisableIntegratedShortcut() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(suggestionsEmpty = false)
        )

        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun zeroQuerySuggestionsAreTreatedAsNonEmptySuggestions() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(suggestionsEmpty = false)
        )

        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun candidateTabVisibilityFalseDisablesCandidateTab() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(candidateTabVisible = false, candidatesShown = true)
        )

        assertFalse(presentation.showCandidateTab)
    }

    @Test
    fun candidateTabVisibilityTrueDoesNotShowCandidateTabWhenCandidatesAreHidden() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(candidateTabVisible = true, candidatesShown = false)
        )

        assertFalse(presentation.showCandidateTab)
    }

    @Test
    fun candidateTabVisibilityTrueShowsCandidateTabWhenCandidatesAreShown() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(
                candidateTabVisible = true,
                candidatesShown = true,
                inputStringEmpty = false
            )
        )

        assertTrue(presentation.showCandidateTab)
    }

    @Test
    fun candidateTabIsHiddenWhenInputStringIsEmptyEvenIfCandidatesAreMarkedShown() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(
                candidateTabVisible = true,
                candidatesShown = true,
                inputStringEmpty = true
            )
        )

        assertFalse(presentation.showCandidateTab)
    }

    @Test
    fun candidateStripUsesEmptyStateWhenCandidatesAreMarkedShownForEmptyInput() {
        assertFalse(
            isCandidateStripActive(
                candidatesShown = true,
                inputStringEmpty = true
            )
        )
        assertEquals(
            80,
            resolveCandidateStripHeightDp(
                candidatesShown = isCandidateStripActive(
                    candidatesShown = true,
                    inputStringEmpty = true
                ),
                candidateHeightDp = 160,
                emptyHeightDp = 80
            )
        )
    }

    @Test
    fun visibleCandidateTabAlwaysReservesItsHeight() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(
                candidateTabVisible = true,
                candidatesShown = true,
                inputStringEmpty = false
            )
        )

        assertEquals(36, resolveCandidateTabOffsetPx(presentation, 36))
    }

    @Test
    fun hiddenCandidateTabDoesNotReserveHeight() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(candidateTabVisible = true, candidatesShown = false)
        )

        assertEquals(0, resolveCandidateTabOffsetPx(presentation, 36))
    }

    @Test
    fun candidatesUseConfiguredCandidateStripHeight() {
        assertEquals(
            160,
            resolveCandidateStripHeightDp(
                candidatesShown = true,
                candidateHeightDp = 160,
                emptyHeightDp = 110
            )
        )
    }

    @Test
    fun emptyStateUsesConfiguredEmptyStripHeight() {
        assertEquals(
            110,
            resolveCandidateStripHeightDp(
                candidatesShown = false,
                candidateHeightDp = 160,
                emptyHeightDp = 110
            )
        )
    }

    @Test
    fun standardSixtyDpHeightIsUsedForBothCandidateStates() {
        assertEquals(
            60,
            resolveCandidateStripHeightDp(
                candidatesShown = true,
                candidateHeightDp = 60,
                emptyHeightDp = 60
            )
        )
        assertEquals(
            60,
            resolveCandidateStripHeightDp(
                candidatesShown = false,
                candidateHeightDp = 60,
                emptyHeightDp = 60
            )
        )
    }

    @Test
    fun explicitAsymmetricHeightsRemainDistinct() {
        assertEquals(
            80,
            resolveCandidateStripHeightDp(
                candidatesShown = true,
                candidateHeightDp = 80,
                emptyHeightDp = 60
            )
        )
        assertEquals(
            60,
            resolveCandidateStripHeightDp(
                candidatesShown = false,
                candidateHeightDp = 80,
                emptyHeightDp = 60
            )
        )
    }

    @Test
    fun idleReturnRequestsCandidateTabSelectionReset() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(
                candidatesShown = false,
                resetCandidateTabSelection = true
            )
        )

        assertFalse(presentation.showCandidateTab)
        assertTrue(presentation.resetCandidateTabSelection)
    }

    @Test
    fun candidatesReserveIndependentToolbarSpaceInsteadOfShowingIt() {
        val presentation = CandidateStripPresentationPolicy.resolve(
            baseState(
                shortcutToolbarIntegratedInSuggestion = false,
                candidatesShown = true,
                inputStringEmpty = false,
                shortcutToolbarHiddenForCandidates = true
            )
        )

        assertFalse(presentation.showIndependentShortcutToolbar)
        assertTrue(presentation.reserveIndependentShortcutToolbarSpace)
        assertFalse(presentation.showIntegratedShortcutItems)
        assertFalse(presentation.showIntegratedShortcutEntry)
    }

    @Test
    fun independentToolbarSeparatesVisibleHeightFromTransparentContainerHeight() {
        for ((tabHeight, toolbarHeight, containerHeight) in listOf(
            Triple(0, 32, 612), Triple(0, 36, 616), Triple(0, 72, 652),
            Triple(36, 32, 616), Triple(36, 36, 616), Triple(36, 72, 652)
        )) {
            for (candidatesShown in listOf(false, true, false)) {
                val presentation = CandidateStripPresentationPolicy.resolve(
                    baseState(
                        candidateTabVisible = tabHeight > 0,
                        shortcutToolbarIntegratedInSuggestion = false,
                        candidatesShown = candidatesShown,
                        inputStringEmpty = !candidatesShown,
                        shortcutToolbarHiddenForCandidates = candidatesShown
                    )
                )
                assertEquals(
                    if (candidatesShown) tabHeight else toolbarHeight,
                    resolveDockedCandidateChromeHeightPx(presentation, tabHeight, toolbarHeight)
                )
                assertEquals(containerHeight,
                    resolveDockedCandidateContainerHeightPx(500, 60, 60, tabHeight, toolbarHeight, 20))
            }
        }
    }

    @Test
    fun transparentContainerReservesTheLargerConfiguredVisibleState() {
        assertEquals(636, resolveDockedCandidateContainerHeightPx(500, 60, 80, 36, 32, 20))
        assertEquals(632, resolveDockedCandidateContainerHeightPx(500, 80, 60, 36, 32, 20))
    }

    @Test
    fun disabledOrIntegratedToolbarKeepsOnlyVisibleCandidateTabHeight() {
        for (state in listOf(baseState(shortcutToolbarVisible = false), baseState())) {
            for (candidatesShown in listOf(false, true)) {
                val presentation = CandidateStripPresentationPolicy.resolve(
                    state.copy(candidatesShown = candidatesShown, inputStringEmpty = !candidatesShown)
                )
                assertEquals(
                    if (candidatesShown) 36 else 0,
                    resolveDockedCandidateChromeHeightPx(presentation, 36, 72)
                )
            }
        }
    }

    @Test
    fun stableContainerCoversAllConfiguredColumnHeightsWithoutAToolbar() {
        // active height, empty height, tab height, expected total including body/inset
        val cases = listOf(
            listOf(60, 48, 0, 580), listOf(60, 48, 36, 616),
            listOf(80, 60, 0, 600), listOf(80, 60, 36, 636),
            listOf(100, 60, 0, 620), listOf(100, 60, 36, 656),
            listOf(60, 120, 0, 640), listOf(60, 120, 36, 640)
        )
        for ((candidateHeight, emptyHeight, tabHeight, expectedHeight) in cases) {
            assertEquals(
                expectedHeight,
                resolveDockedCandidateContainerHeightPx(500, emptyHeight, candidateHeight, tabHeight, 0, 20)
            )
        }
    }

    @Test
    fun stableInsetsIgnoreInputAndConfirmationHeightChanges() {
        for (visibleTop in listOf(100, 136, 160, 100)) {
            assertEquals(100, resolveDockedCandidateInsetsTopPx(true, 100, visibleTop))
            assertEquals(visibleTop, resolveDockedCandidateInsetsTopPx(false, 100, visibleTop))
        }
    }

    private fun baseState(
        candidateTabVisible: Boolean = true,
        candidatesShown: Boolean = false,
        resetCandidateTabSelection: Boolean = false,
        shortcutToolbarVisible: Boolean = true,
        shortcutToolbarIntegratedInSuggestion: Boolean = true,
        inputStringEmpty: Boolean = true,
        tailEmpty: Boolean = true,
        clipboardPreviewShown: Boolean = false,
        selectionActionsShown: Boolean = false,
        suggestionsEmpty: Boolean = true,
        customLayoutPickerShown: Boolean = false,
        symbolKeyboardShown: Boolean = false,
        shortcutToolbarHiddenForCandidates: Boolean = false
    ): CandidateStripPresentationState {
        return CandidateStripPresentationState(
            candidateTabVisible = candidateTabVisible,
            candidatesShown = candidatesShown,
            resetCandidateTabSelection = resetCandidateTabSelection,
            shortcutToolbarVisible = shortcutToolbarVisible,
            shortcutToolbarIntegratedInSuggestion = shortcutToolbarIntegratedInSuggestion,
            inputStringEmpty = inputStringEmpty,
            tailEmpty = tailEmpty,
            clipboardPreviewShown = clipboardPreviewShown,
            selectionActionsShown = selectionActionsShown,
            suggestionsEmpty = suggestionsEmpty,
            customLayoutPickerShown = customLayoutPickerShown,
            symbolKeyboardShown = symbolKeyboardShown,
            shortcutToolbarHiddenForCandidates = shortcutToolbarHiddenForCandidates
        )
    }
}
