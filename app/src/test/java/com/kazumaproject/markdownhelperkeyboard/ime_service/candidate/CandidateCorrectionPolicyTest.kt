package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.converter.session.CandidateQueryMode
import org.junit.Assert.*
import org.junit.Test

class CandidateCorrectionPolicyTest {
    @Test fun japaneseCorrectionsFollowKeyboardAndQueryMode() {
        for (mode in CandidateQueryMode.entries) {
            for (keyboard in listOf(TenKeyQWERTYMode.Default, TenKeyQWERTYMode.Sumire,
                TenKeyQWERTYMode.Gojuon, TenKeyQWERTYMode.TenKeyQWERTY,
                TenKeyQWERTYMode.TenKeyQWERTYRomaji, TenKeyQWERTYMode.Custom)) {
                val result = resolve(mode, keyboard)
                assertEquals(mode != CandidateQueryMode.EISUKANA, result.omissionEnabled)
                val eligible = mode != CandidateQueryMode.EISUKANA &&
                    (keyboard == TenKeyQWERTYMode.Default || keyboard == TenKeyQWERTYMode.Sumire ||
                        (keyboard == TenKeyQWERTYMode.Gojuon && mode != CandidateQueryMode.PREDICTION))
                assertEquals("$mode/$keyboard", eligible, result.japaneseFlickEnabled)
            }
        }
    }

    @Test fun disabledPreferencesStayDisabledInEveryMode() {
        for (mode in CandidateQueryMode.entries) {
            assertEquals(CandidateCorrectionOptions(false, false, false),
                CandidateCorrectionPolicy.resolve(mode, TenKeyQWERTYMode.Default,
                    false, false, false, false))
        }
    }

    @Test fun englishCorrectionPreservesPredictionOnlyKeyboardConditions() {
        for (mode in CandidateQueryMode.entries) for (romaji in listOf(false, true)) {
            val eligibleMode = mode == CandidateQueryMode.NO_TAB_DEFAULT || mode == CandidateQueryMode.PREDICTION
            assertEquals(eligibleMode, resolve(mode, TenKeyQWERTYMode.TenKeyQWERTY, romaji).englishQwertyEnabled)
            assertEquals(eligibleMode && !romaji,
                resolve(mode, TenKeyQWERTYMode.TenKeyQWERTYRomaji, romaji).englishQwertyEnabled)
            assertFalse(resolve(mode, TenKeyQWERTYMode.Default, romaji).englishQwertyEnabled)
        }
    }

    private fun resolve(mode: CandidateQueryMode, keyboard: TenKeyQWERTYMode, romaji: Boolean = false) =
        CandidateCorrectionPolicy.resolve(mode, keyboard, true, true, true, romaji)
}
