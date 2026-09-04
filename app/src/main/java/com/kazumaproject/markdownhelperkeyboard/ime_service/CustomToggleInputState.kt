package com.kazumaproject.markdownhelperkeyboard.ime_service

internal class CustomToggleInputState {
    sealed interface Mutation {
        data class Append(val text: String) : Mutation
        data class Replace(val previous: String, val next: String) : Mutation
    }

    private var keyIdentity: String? = null
    private var values: List<String> = emptyList()
    private var index = -1

    fun next(keyIdentity: String, values: List<String>): Mutation? {
        if (values.isEmpty()) {
            reset()
            return null
        }
        if (values.size == 1) {
            reset()
            return Mutation.Append(values.first())
        }
        if (this.keyIdentity != keyIdentity || this.values != values || index !in values.indices) {
            this.keyIdentity = keyIdentity
            this.values = values
            index = 0
            return Mutation.Append(values.first())
        }
        val previous = values[index]
        index = (index + 1) % values.size
        return Mutation.Replace(previous, values[index])
    }

    fun reset() {
        keyIdentity = null
        values = emptyList()
        index = -1
    }
}
