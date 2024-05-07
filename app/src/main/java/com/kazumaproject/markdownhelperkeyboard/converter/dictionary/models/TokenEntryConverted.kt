package com.kazumaproject.dictionary.models

data class TokenEntryConverted(
    val leftId: Short,
    val rightId: Short,
    val wordCost: Short,
    val tango: String,
    val yomiLength: Short,
)
