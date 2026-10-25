package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ConversionCorrection
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateCorrectionOptions

internal object ZenzInputPolicy {
    fun enabledMask(options: CandidateCorrectionOptions): Int =
        (if (options.omissionEnabled) ConversionCorrection.OMISSION else 0) or
            (if (options.japaneseFlickEnabled) ConversionCorrection.JAPANESE_FLICK else 0) or
            (if (options.englishQwertyEnabled) ConversionCorrection.ENGLISH_QWERTY else 0)

    fun reading(input: String, bestNBestCandidate: Candidate?, options: CandidateCorrectionOptions): String {
        val candidate = bestNBestCandidate ?: return input
        if (candidate.correctionMask and enabledMask(options) == 0) return input
        return candidate.yomi?.takeIf { it.isNotEmpty() } ?: input
    }
}
