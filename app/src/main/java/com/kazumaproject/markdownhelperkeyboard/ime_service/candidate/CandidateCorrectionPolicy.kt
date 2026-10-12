package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.converter.session.CandidateQueryMode

internal data class CandidateCorrectionOptions(
    val omissionEnabled: Boolean,
    val japaneseFlickEnabled: Boolean,
    val englishQwertyEnabled: Boolean,
)

/** Conversion disables prediction, not the Japanese input corrections used to build its lattice. */
internal object CandidateCorrectionPolicy {
    fun resolve(
        queryMode: CandidateQueryMode,
        keyboardMode: TenKeyQWERTYMode,
        omissionEnabled: Boolean,
        japaneseFlickEnabled: Boolean,
        englishQwertyEnabled: Boolean,
        qwertyRomajiMode: Boolean,
    ): CandidateCorrectionOptions {
        if (queryMode == CandidateQueryMode.EISUKANA) {
            return CandidateCorrectionOptions(false, false, false)
        }
        val japaneseKeyboard = keyboardMode == TenKeyQWERTYMode.Default ||
            keyboardMode == TenKeyQWERTYMode.Sumire ||
            // Preserve the original no-tab behavior; the prediction tab excludes Gojuon.
            (keyboardMode == TenKeyQWERTYMode.Gojuon && queryMode != CandidateQueryMode.PREDICTION)
        val englishKeyboard = keyboardMode == TenKeyQWERTYMode.TenKeyQWERTY ||
            (keyboardMode == TenKeyQWERTYMode.TenKeyQWERTYRomaji && !qwertyRomajiMode)
        return CandidateCorrectionOptions(
            omissionEnabled = omissionEnabled,
            japaneseFlickEnabled = japaneseFlickEnabled && japaneseKeyboard,
            englishQwertyEnabled = englishQwertyEnabled && englishKeyboard &&
                queryMode != CandidateQueryMode.CONVERSION,
        )
    }
}
