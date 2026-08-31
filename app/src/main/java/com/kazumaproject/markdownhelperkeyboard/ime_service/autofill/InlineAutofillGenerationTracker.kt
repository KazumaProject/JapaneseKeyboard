package com.kazumaproject.markdownhelperkeyboard.ime_service.autofill

/** Rejects asynchronous inline-suggestion results belonging to an old editor or response. */
internal class InlineAutofillGenerationTracker {

    data class Token internal constructor(
        val sessionGeneration: Long,
        val responseGeneration: Long,
    )

    private var sessionGeneration = 0L
    private var responseGeneration = 0L

    @Synchronized
    fun startInputSession(): Token {
        sessionGeneration += 1L
        responseGeneration += 1L
        return currentToken()
    }

    @Synchronized
    fun beginResponse(): Token {
        responseGeneration += 1L
        return currentToken()
    }

    @Synchronized
    fun invalidateResponse(): Token {
        responseGeneration += 1L
        return currentToken()
    }

    @Synchronized
    fun isCurrent(token: Token): Boolean = token == currentToken()

    private fun currentToken() = Token(
        sessionGeneration = sessionGeneration,
        responseGeneration = responseGeneration,
    )
}
