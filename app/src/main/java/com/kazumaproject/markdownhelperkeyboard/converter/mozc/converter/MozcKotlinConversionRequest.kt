package com.kazumaproject.markdownhelperkeyboard.converter.mozc.converter

import com.kazumaproject.markdownhelperkeyboard.converter.mozc.dictionary.MozcDictionaryLookupOptions
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.dictionary.RuntimeMozcLearnDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.dictionary.RuntimeMozcUserDictionary

data class MozcKotlinConversionRequest(
    val input: String,
    val nBest: Int,
    val dictionaryOptions: MozcDictionaryLookupOptions,
    val runtimeUserDictionary: RuntimeMozcUserDictionary?,
    val runtimeLearnDictionary: RuntimeMozcLearnDictionary?,
    val options: MozcKotlinConversionOptions = MozcKotlinConversionOptions(),
)

data class MozcKotlinConversionOptions(
    val disablePrefixPenalty: Boolean = false,
    val disableSuffixPenalty: Boolean = false,
    val isSingleSegment: Boolean = false,
)
