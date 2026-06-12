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

internal object RuntimeInputBehaviorPolicy {
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
    ): ResolvedInputBehavior {
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
