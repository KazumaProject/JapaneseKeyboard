package com.kazumaproject.markdownhelperkeyboard.converter.api

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

data class JapaneseConversionResult(
    val engineId: JapaneseConversionEngineId,
    val candidates: List<Candidate>,
    val bunsetsuResult: BunsetsuCandidateResult? = null,
)
