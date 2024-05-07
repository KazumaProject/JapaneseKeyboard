package com.kazumaproject.dictionary.models

data class Dictionary(
    val yomi: String,
    val leftId: Short,
    val rightId: Short,
    val cost: Short,
    val tango: String
)
