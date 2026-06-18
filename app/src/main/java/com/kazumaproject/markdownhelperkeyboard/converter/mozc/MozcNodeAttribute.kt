package com.kazumaproject.markdownhelperkeyboard.converter.mozc

object MozcNodeAttribute {
    const val DEFAULT = 0
    const val SYSTEM_DICTIONARY = 1 shl 0
    const val USER_DICTIONARY = 1 shl 1
    const val NO_VARIANTS_EXPANSION = 1 shl 2
    const val STARTS_WITH_PARTICLE = 1 shl 4
    const val SPELLING_CORRECTION = 1 shl 5
    const val OMISSION_SEARCH = 1 shl 6
    const val PARTIALLY_KEY_CONSUMED = 1 shl 7
    const val SUFFIX_DICTIONARY = 1 shl 8
    const val KEY_EXPANDED = 1 shl 9
    const val UNKNOWN = 1 shl 16
}
