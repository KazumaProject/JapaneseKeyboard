package com.kazumaproject.markdownhelperkeyboard.learning.multiple

class LearnMultiple {
    private var isEnabled: Boolean = false
    private var input: String? = null
    private var stringBuilder: StringBuilder = StringBuilder()

    fun enabled(): Boolean {
        return isEnabled
    }

    fun setInput(inputFromIME: String): Boolean {
        input = inputFromIME
        return true
    }

    fun getInput(): String {
        return input ?: ""
    }

    fun setWordToStringBuilder(word: String) {
        stringBuilder.append(word)
    }

    fun getInputAndStringBuilder(): Pair<String, String> {
        return Pair(input ?: "", stringBuilder.toString())
    }

    fun start() {
        isEnabled = true
    }

    fun stop() {
        clearInput()
        clearStringBuilder()
        isEnabled = false
    }

    private fun clearInput() {
        input = null
    }

    private fun clearStringBuilder() {
        stringBuilder.clear()
    }
}