package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.ime_service.models.CandidateShowFlag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the latest candidate refresh request for one IME process.
 *
 * A refresh is state, rather than a rendezvous event: producers must never wait for candidate UI
 * work because they run on the text-input path. The monotonically increasing revision makes two
 * consecutive requests with the same flag distinct while StateFlow keeps memory usage bounded.
 */
internal class CandidateRefreshCoordinator {
    private var sessionId: Long = 0L
    private var revision: Long = 0L

    private val _requests = MutableStateFlow(
        CandidateRefreshRequest(
            sessionId = sessionId,
            revision = revision,
            input = "",
            flag = CandidateShowFlag.Idle,
        )
    )
    val requests: StateFlow<CandidateRefreshRequest> = _requests.asStateFlow()

    @Synchronized
    fun restart(): CandidateRefreshRequest {
        sessionId += 1L
        revision = 0L
        return publishLocked(input = "", flag = CandidateShowFlag.Idle)
    }

    @Synchronized
    fun invalidate(): CandidateRefreshRequest {
        sessionId += 1L
        revision = 0L
        return publishLocked(input = "", flag = CandidateShowFlag.Idle)
    }

    @Synchronized
    fun request(
        input: String,
        flag: CandidateShowFlag,
    ): CandidateRefreshRequest = publishLocked(input, flag)

    @Synchronized
    fun isCurrent(request: CandidateRefreshRequest): Boolean = _requests.value == request

    private fun publishLocked(
        input: String,
        flag: CandidateShowFlag,
    ): CandidateRefreshRequest {
        revision += 1L
        return CandidateRefreshRequest(
            sessionId = sessionId,
            revision = revision,
            input = input,
            flag = flag,
        ).also { _requests.value = it }
    }
}

internal data class CandidateRefreshRequest(
    val sessionId: Long,
    val revision: Long,
    val input: String,
    val flag: CandidateShowFlag,
)
