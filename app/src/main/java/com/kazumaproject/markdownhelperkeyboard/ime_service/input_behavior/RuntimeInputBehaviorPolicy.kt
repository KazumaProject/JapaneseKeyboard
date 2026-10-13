package com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior

import com.kazumaproject.core.domain.state.TenKeyQWERTYMode

internal data class RuntimeInputBehaviorSafetyState(
    val inputStringEmpty: Boolean,
    val tailEmpty: Boolean,
    val henkanActive: Boolean,
    val bunsetsuMultipleDetect: Boolean,
    val henkanPressedWithBunsetsuDetect: Boolean,
    val bunsetsuConversionSessionActive: Boolean,
    val bunsetsuCursorMoveSessionActive: Boolean,
    val candidateHighlightActive: Boolean,
)

internal enum class DirectCommitTransition {
    NONE,
    CLEAR,
    FINISH_AND_CLEAR,
}

internal object RuntimeInputBehaviorPolicy {
    fun directCommitTransition(
        previous: ResolvedInputBehavior,
        current: ResolvedInputBehavior,
        startingNewInput: Boolean,
        canToggleSafely: Boolean,
        replaceComposingOnNextInput: Boolean = false,
    ): DirectCommitTransition {
        if (current != ResolvedInputBehavior.DIRECT_COMMIT) return DirectCommitTransition.NONE
        if (startingNewInput) return DirectCommitTransition.CLEAR
        if (previous == ResolvedInputBehavior.DIRECT_COMMIT) return DirectCommitTransition.NONE
        // Preserve both composing states until the first direct commit replaces the span.
        if (replaceComposingOnNextInput && !canToggleSafely) return DirectCommitTransition.NONE
        return if (canToggleSafely) {
            DirectCommitTransition.CLEAR
        } else {
            DirectCommitTransition.FINISH_AND_CLEAR
        }
    }

    fun resolveBaseline(
        qwertyMode: TenKeyQWERTYMode,
        isCustomLayoutDirectMode: Boolean,
        resolvedInputBehavior: ResolvedInputBehavior,
    ): ResolvedInputBehavior {
        return if (qwertyMode == TenKeyQWERTYMode.Custom && isCustomLayoutDirectMode) {
            ResolvedInputBehavior.DIRECT_COMMIT
        } else {
            resolvedInputBehavior
        }
    }

    fun effective(
        baseline: ResolvedInputBehavior,
        shortcutOverride: ResolvedInputBehavior?,
        forceDirectCommit: Boolean = false,
    ): ResolvedInputBehavior {
        if (forceDirectCommit) return ResolvedInputBehavior.DIRECT_COMMIT
        return shortcutOverride ?: baseline
    }

    fun toggledOverride(current: ResolvedInputBehavior): ResolvedInputBehavior {
        return when (current) {
            ResolvedInputBehavior.DIRECT_COMMIT -> ResolvedInputBehavior.COMPOSING_TEXT
            ResolvedInputBehavior.COMPOSING_TEXT -> ResolvedInputBehavior.DIRECT_COMMIT
        }
    }

    fun canToggle(state: RuntimeInputBehaviorSafetyState): Boolean {
        return state.inputStringEmpty &&
                state.tailEmpty &&
                !state.henkanActive &&
                !state.bunsetsuMultipleDetect &&
                !state.henkanPressedWithBunsetsuDetect &&
                !state.bunsetsuConversionSessionActive &&
                !state.bunsetsuCursorMoveSessionActive &&
                !state.candidateHighlightActive
    }
}
