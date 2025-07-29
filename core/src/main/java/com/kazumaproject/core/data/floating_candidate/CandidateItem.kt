package com.kazumaproject.core.data.floating_candidate

sealed interface CandidateItem {
    data class Suggestion(val text: String) : CandidateItem
    data class Pager(val label: String) : CandidateItem
}
