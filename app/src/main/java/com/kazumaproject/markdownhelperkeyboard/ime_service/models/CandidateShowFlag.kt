package com.kazumaproject.markdownhelperkeyboard.ime_service.models

sealed class CandidateShowFlag {
    data object Idle : CandidateShowFlag()
    data object Updating : CandidateShowFlag()
}
