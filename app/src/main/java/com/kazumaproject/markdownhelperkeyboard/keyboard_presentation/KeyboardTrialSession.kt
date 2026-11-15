package com.kazumaproject.markdownhelperkeyboard.keyboard_presentation

import android.view.inputmethod.EditorInfo
import java.util.UUID

/** App-private, process-local lease. Never accepts a target supplied by another editor. */
internal object KeyboardTrialSession {
    const val EXTRA_TOKEN = "com.kazumaproject.keyboard.trial.token"
    private data class Lease(val token: String, val target: KeyboardPreviewTarget)
    private var lease: Lease? = null
    private val activeState = kotlinx.coroutines.flow.MutableStateFlow(false)
    val active: kotlinx.coroutines.flow.StateFlow<Boolean> = activeState
    private val failure = kotlinx.coroutines.flow.MutableStateFlow(false)
    val unavailable: kotlinx.coroutines.flow.StateFlow<Boolean> = failure
    @Synchronized fun reportUnavailable() { failure.value = true }
    data class CandidateResult(val target: KeyboardPreviewTarget, val input: String,
        val candidates: List<com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate>)
    private val result = kotlinx.coroutines.flow.MutableStateFlow<CandidateResult?>(null)
    val candidates: kotlinx.coroutines.flow.StateFlow<CandidateResult?> = result
    @Synchronized fun publish(target: KeyboardPreviewTarget, input: String,
        candidates: List<com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate>) {
        if (lease?.target == target && input.isNotEmpty() && candidates.isNotEmpty())
            result.value = CandidateResult(target, input, candidates.toList())
    }

    @Synchronized fun begin(target: KeyboardPreviewTarget): String = UUID.randomUUID().toString().also {
        lease = Lease(it, target); activeState.value = true; result.value = null; failure.value = false
    }
    @Synchronized fun end(token: String) { if (lease?.token == token) { lease = null; activeState.value = false } }
    @Synchronized fun resolve(editor: EditorInfo?, ownPackage: String): KeyboardPreviewTarget? {
        if (editor?.packageName != ownPackage) return null
        val token = editor.extras?.getString(EXTRA_TOKEN) ?: return null
        return lease?.takeIf { it.token == token }?.target
    }
}
