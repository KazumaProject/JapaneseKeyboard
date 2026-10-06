package com.kazumaproject.markdownhelperkeyboard.user_dictionary

/** Normalizes manually entered words without allowing whitespace-only entries. */
internal object DictionaryWordInput {
    fun normalize(word: String, preserveWhitespace: Boolean): String? {
        if (word.isBlank()) return null
        return if (preserveWhitespace) word else word.trim()
    }
}
