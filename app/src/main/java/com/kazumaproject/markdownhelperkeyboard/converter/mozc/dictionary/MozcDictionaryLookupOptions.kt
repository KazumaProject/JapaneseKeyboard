package com.kazumaproject.markdownhelperkeyboard.converter.mozc.dictionary

data class MozcDictionaryLookupOptions(
    val enablePersonName: Boolean,
    val enablePlaces: Boolean,
    val enableWiki: Boolean,
    val enableNeologd: Boolean,
    val enableWeb: Boolean,
)
