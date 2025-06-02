package com.kazumaproject.core.data.qwerty

data class VariationInfo(
    val tap: Char?,
    val cap: Char?,
    val variations: List<Char>?,
    val capVariations: List<Char>?
)
