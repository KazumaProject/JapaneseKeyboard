package com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior

import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeInputBehaviorPolicyTest {

    @Test
    fun typeNullDefaultAndDirectCommitBaselineResolveDirectCommit() {
        listOf(
            TypeNullInputBehaviorSetting.DEFAULT,
            TypeNullInputBehaviorSetting.DIRECT_COMMIT,
        ).forEach { setting ->
            val resolved = InputBehaviorResolver { setting }.resolve(InputTypeForIME.TypeNull)

            assertEquals(
                ResolvedInputBehavior.DIRECT_COMMIT,
                RuntimeInputBehaviorPolicy.resolveBaseline(
                    qwertyMode = TenKeyQWERTYMode.Default,
                    isCustomLayoutDirectMode = false,
                    resolvedInputBehavior = resolved,
                )
            )
        }
    }

    @Test
    fun typeNullComposingTextBaselineResolvesComposingText() {
        val resolved = InputBehaviorResolver {
            TypeNullInputBehaviorSetting.COMPOSING_TEXT
        }.resolve(InputTypeForIME.TypeNull)

        assertEquals(
            ResolvedInputBehavior.COMPOSING_TEXT,
            RuntimeInputBehaviorPolicy.resolveBaseline(
                qwertyMode = TenKeyQWERTYMode.Default,
                isCustomLayoutDirectMode = false,
                resolvedInputBehavior = resolved,
            )
        )
    }

    @Test
    fun normalTextBaselineResolvesComposingText() {
        val resolved = InputBehaviorResolver {
            TypeNullInputBehaviorSetting.DIRECT_COMMIT
        }.resolve(InputTypeForIME.Text)

        assertEquals(
            ResolvedInputBehavior.COMPOSING_TEXT,
            RuntimeInputBehaviorPolicy.resolveBaseline(
                qwertyMode = TenKeyQWERTYMode.Default,
                isCustomLayoutDirectMode = false,
                resolvedInputBehavior = resolved,
            )
        )
    }

    @Test
    fun customKeyboardDirectModeOverridesBaselineToDirectCommit() {
        assertEquals(
            ResolvedInputBehavior.DIRECT_COMMIT,
            RuntimeInputBehaviorPolicy.resolveBaseline(
                qwertyMode = TenKeyQWERTYMode.Custom,
                isCustomLayoutDirectMode = true,
                resolvedInputBehavior = ResolvedInputBehavior.COMPOSING_TEXT,
            )
        )
    }

    @Test
    fun shortcutOverrideWinsUntilReset() {
        assertEquals(
            ResolvedInputBehavior.DIRECT_COMMIT,
            RuntimeInputBehaviorPolicy.effective(
                baseline = ResolvedInputBehavior.COMPOSING_TEXT,
                shortcutOverride = ResolvedInputBehavior.DIRECT_COMMIT,
            )
        )
        assertEquals(
            ResolvedInputBehavior.COMPOSING_TEXT,
            RuntimeInputBehaviorPolicy.effective(
                baseline = ResolvedInputBehavior.COMPOSING_TEXT,
                shortcutOverride = null,
            )
        )
    }

    @Test
    fun toggleFlipsCurrentBehavior() {
        assertEquals(
            ResolvedInputBehavior.COMPOSING_TEXT,
            RuntimeInputBehaviorPolicy.toggledOverride(ResolvedInputBehavior.DIRECT_COMMIT)
        )
        assertEquals(
            ResolvedInputBehavior.DIRECT_COMMIT,
            RuntimeInputBehaviorPolicy.toggledOverride(ResolvedInputBehavior.COMPOSING_TEXT)
        )
    }

    @Test
    fun canToggleOnlyWhenNoInputOrConversionStateIsActive() {
        assertTrue(RuntimeInputBehaviorPolicy.canToggle(safeState()))

        assertFalse(RuntimeInputBehaviorPolicy.canToggle(safeState(inputStringEmpty = false)))
        assertFalse(RuntimeInputBehaviorPolicy.canToggle(safeState(tailEmpty = false)))
        assertFalse(RuntimeInputBehaviorPolicy.canToggle(safeState(henkanActive = true)))
        assertFalse(RuntimeInputBehaviorPolicy.canToggle(safeState(bunsetsuMultipleDetect = true)))
        assertFalse(
            RuntimeInputBehaviorPolicy.canToggle(
                safeState(henkanPressedWithBunsetsuDetect = true)
            )
        )
        assertFalse(
            RuntimeInputBehaviorPolicy.canToggle(
                safeState(bunsetsuConversionSessionActive = true)
            )
        )
        assertFalse(
            RuntimeInputBehaviorPolicy.canToggle(
                safeState(bunsetsuCursorMoveSessionActive = true)
            )
        )
        assertFalse(RuntimeInputBehaviorPolicy.canToggle(safeState(candidateHighlightActive = true)))
    }

    private fun safeState(
        inputStringEmpty: Boolean = true,
        tailEmpty: Boolean = true,
        henkanActive: Boolean = false,
        bunsetsuMultipleDetect: Boolean = false,
        henkanPressedWithBunsetsuDetect: Boolean = false,
        bunsetsuConversionSessionActive: Boolean = false,
        bunsetsuCursorMoveSessionActive: Boolean = false,
        candidateHighlightActive: Boolean = false,
    ): RuntimeInputBehaviorSafetyState {
        return RuntimeInputBehaviorSafetyState(
            inputStringEmpty = inputStringEmpty,
            tailEmpty = tailEmpty,
            henkanActive = henkanActive,
            bunsetsuMultipleDetect = bunsetsuMultipleDetect,
            henkanPressedWithBunsetsuDetect = henkanPressedWithBunsetsuDetect,
            bunsetsuConversionSessionActive = bunsetsuConversionSessionActive,
            bunsetsuCursorMoveSessionActive = bunsetsuCursorMoveSessionActive,
            candidateHighlightActive = candidateHighlightActive,
        )
    }
}
