package com.kazumaproject.markdownhelperkeyboard.converter.mozc.dictionary

data class MozcDictionaryToken(
    val key: String,
    val value: String,
    val lid: Short,
    val rid: Short,
    val cost: Int,
    val source: MozcDictionarySource,
)

enum class MozcDictionarySource {
    SYSTEM,
    SYSTEM_USER,
    USER,
    LEARN,
    WIKI,
    WEB,
    PERSON,
    PLACES,
    NEOLOGD,
}
