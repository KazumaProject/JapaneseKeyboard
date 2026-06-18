package com.kazumaproject.markdownhelperkeyboard.converter.mozc

interface MozcDictionary {
    fun lookupPrefix(
        key: String,
        beginPos: Int,
        options: MozcConversionOptions,
        builder: MozcNodeListBuilder,
    )

    fun lookupExact(
        key: String,
        beginPos: Int,
        options: MozcConversionOptions,
        builder: MozcNodeListBuilder,
    )

    fun lookupPredictive(
        key: String,
        beginPos: Int,
        options: MozcConversionOptions,
        builder: MozcNodeListBuilder,
    )

    fun lookupReverse(
        value: String,
        options: MozcConversionOptions,
        builder: MozcNodeListBuilder,
    )
}
