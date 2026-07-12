package com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior

import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QwertyEnglishDirectInputPolicyTest {

    @Test
    fun enabledEnglishQwertyForcesDirectCommit() {
        assertTrue(
            shouldForce(
                qwertyMode = TenKeyQWERTYMode.TenKeyQWERTY,
                inputMode = InputMode.ModeEnglish,
                romajiMode = false,
            )
        )
        assertTrue(
            shouldForce(
                qwertyMode = TenKeyQWERTYMode.TenKeyQWERTYRomaji,
                inputMode = InputMode.ModeEnglish,
                romajiMode = false,
            )
        )
    }

    @Test
    fun disabledPreferenceNeverForcesDirectCommit() {
        assertFalse(
            shouldForce(
                preferenceEnabled = false,
                qwertyMode = TenKeyQWERTYMode.TenKeyQWERTY,
                inputMode = InputMode.ModeEnglish,
                romajiMode = false,
            )
        )
    }

    @Test
    fun romajiAndNumberModesAreNotForced() {
        assertFalse(
            shouldForce(
                qwertyMode = TenKeyQWERTYMode.TenKeyQWERTYRomaji,
                inputMode = InputMode.ModeJapanese,
                romajiMode = true,
            )
        )
        assertFalse(
            shouldForce(
                qwertyMode = TenKeyQWERTYMode.TenKeyQWERTY,
                inputMode = InputMode.ModeNumber,
                romajiMode = false,
            )
        )
    }

    @Test
    fun nonQwertyLayoutsAreNotForced() {
        listOf(
            TenKeyQWERTYMode.Default,
            TenKeyQWERTYMode.Custom,
            TenKeyQWERTYMode.Sumire,
            TenKeyQWERTYMode.Number,
        ).forEach { mode ->
            assertFalse(
                shouldForce(
                    qwertyMode = mode,
                    inputMode = InputMode.ModeEnglish,
                    romajiMode = false,
                )
            )
        }
    }

    private fun shouldForce(
        preferenceEnabled: Boolean = true,
        qwertyMode: TenKeyQWERTYMode,
        inputMode: InputMode,
        romajiMode: Boolean,
    ): Boolean = QwertyEnglishDirectInputPolicy.shouldForceDirectCommit(
        preferenceEnabled = preferenceEnabled,
        qwertyMode = qwertyMode,
        inputMode = inputMode,
        currentQwertyRomajiModeForSession = romajiMode,
    )
}
