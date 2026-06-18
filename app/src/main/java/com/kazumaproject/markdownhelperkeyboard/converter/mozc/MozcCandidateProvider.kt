package com.kazumaproject.markdownhelperkeyboard.converter.mozc

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

interface MozcCandidateProvider {
    fun getCandidates(
        input: String,
        options: MozcConversionOptions = MozcConversionOptions(),
    ): List<Candidate>
}
