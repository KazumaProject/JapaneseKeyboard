package com.kazumaproject.markdownhelperkeyboard.converter.mozc.dictionary

interface MozcDictionary {
    fun lookupPrefix(
        key: String,
        callback: (MozcDictionaryToken) -> Unit,
    )

    fun lookupExact(
        key: String,
        callback: (MozcDictionaryToken) -> Unit,
    )
}
