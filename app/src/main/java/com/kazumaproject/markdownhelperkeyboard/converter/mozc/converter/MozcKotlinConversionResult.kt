package com.kazumaproject.markdownhelperkeyboard.converter.mozc.converter

import com.kazumaproject.markdownhelperkeyboard.converter.mozc.model.MozcCandidate
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.model.MozcSegments

data class MozcKotlinConversionResult(
    val segments: MozcSegments,
    val candidates: List<MozcCandidate>,
)
