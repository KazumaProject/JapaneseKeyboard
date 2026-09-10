package com.kazumaproject.markdownhelperkeyboard.ime_service

internal class CustomToggleInputState {
    sealed interface Mutation {
        data class Append(val text: String) : Mutation
        data class Replace(val previous: String, val next: String) : Mutation
    }

    private var keyIdentity: String? = null
    private var values: List<String> = emptyList()
    private var index = -1
    private var lastEmittedText: String? = null
    private var lastInputTimeMillis: Long? = null
    private var timeoutMillis: Long = 0

    fun remainingMillis(nowMillis: Long): Long {
        val elapsed = lastInputTimeMillis?.let { nowMillis - it } ?: return 0
        if (elapsed < 0) return 0
        return (timeoutMillis - elapsed).coerceAtLeast(0)
    }

    fun next(
        keyIdentity: String,
        values: List<String>,
        outputValues: List<String> = values,
        nowMillis: Long,
        timeoutMillis: Long,
    ): Mutation? {
        if (values.isEmpty() || values.size != outputValues.size) {
            reset()
            return null
        }
        if (values.size == 1) {
            reset()
            return Mutation.Append(outputValues.first())
        }
        val elapsedMillis = lastInputTimeMillis?.let { nowMillis - it }
        val expired = elapsedMillis == null || elapsedMillis < 0 || elapsedMillis >= timeoutMillis
        lastInputTimeMillis = nowMillis
        this.timeoutMillis = timeoutMillis
        if (
            expired ||
            this.keyIdentity != keyIdentity ||
            this.values != values ||
            index !in values.indices ||
            lastEmittedText == null
        ) {
            this.keyIdentity = keyIdentity
            this.values = values.toList()
            index = 0
            lastEmittedText = outputValues.first()
            return Mutation.Append(lastEmittedText.orEmpty())
        }
        val previous = checkNotNull(lastEmittedText)
        index = (index + 1) % values.size
        val next = outputValues[index]
        lastEmittedText = next
        return Mutation.Replace(previous, next)
    }

    fun reset() {
        keyIdentity = null
        values = emptyList()
        index = -1
        lastEmittedText = null
        lastInputTimeMillis = null
        timeoutMillis = 0
    }
}
