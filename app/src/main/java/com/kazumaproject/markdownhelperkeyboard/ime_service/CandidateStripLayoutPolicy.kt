package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent

object CandidateStripLayoutPolicy {

    fun shouldUseLinearHorizontalLayout(content: CandidateStripContent): Boolean {
        return content is CandidateStripContent.GemmaActions ||
            content is CandidateStripContent.ZeroQuerySuggestions ||
            (content is CandidateStripContent.EmptyState && content.showZeroQueryToggle)
    }
}
