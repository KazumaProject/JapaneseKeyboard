package com.kazumaproject.dictionary.models

import java.io.Serializable

data class TokenEntry(
    val posTableIndex: Short,
    val wordCost: Short,
    val nodeId: Int,
): Serializable
