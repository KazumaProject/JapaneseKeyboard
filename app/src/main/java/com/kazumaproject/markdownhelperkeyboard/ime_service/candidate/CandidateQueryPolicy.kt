package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.session.CandidateQueryMode
import com.kazumaproject.markdownhelperkeyboard.converter.session.ConversionBackend
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.CandidateTab

object CandidateQueryModeResolver {
    fun resolve(
        tabVisible: Boolean,
        tabOrder: List<CandidateTab>,
        selectedPosition: Int,
    ): CandidateQueryMode {
        if (!tabVisible) return CandidateQueryMode.NO_TAB_DEFAULT
        return when (tabOrder.getOrNull(selectedPosition)) {
            CandidateTab.PREDICTION -> CandidateQueryMode.PREDICTION
            CandidateTab.CONVERSION -> CandidateQueryMode.CONVERSION
            CandidateTab.EISUKANA -> CandidateQueryMode.EISUKANA
            null -> CandidateQueryMode.PREDICTION
        }
    }
}

data class CandidateRequestToken internal constructor(
    val sessionId: Long,
    val revision: Long,
    val input: String,
    val mode: CandidateQueryMode,
    val backend: ConversionBackend,
)

/** Rejects an older result even when it was produced for the same input string in another tab. */
class CandidateRequestTracker {
    private var sessionId: Long = 0
    private var revision: Long = 0
    private var current: CandidateRequestToken? = null

    @Synchronized
    fun restart(backend: ConversionBackend) {
        sessionId += 1
        revision = 0
        current = CandidateRequestToken(
            sessionId = sessionId,
            revision = revision,
            input = "",
            mode = CandidateQueryMode.NO_TAB_DEFAULT,
            backend = backend,
        )
    }

    @Synchronized
    fun begin(
        input: String,
        mode: CandidateQueryMode,
        backend: ConversionBackend,
    ): CandidateRequestToken {
        revision += 1
        return CandidateRequestToken(
            sessionId = sessionId,
            revision = revision,
            input = input,
            mode = mode,
            backend = backend,
        ).also { current = it }
    }

    @Synchronized
    fun invalidate() {
        revision += 1
        current = null
    }

    @Synchronized
    fun isCurrent(token: CandidateRequestToken): Boolean = current == token
}
