package com.kazumaproject.markdownhelperkeyboard.converter.candidate

/**
 * @see 1:NBest 2:Part of letters 3:Hirakana 4:Katakana 5:Combine part of letter 6. Single Kanji
 **/
data class Candidate (
    val string: String,
    val type: Byte,
    val length: UByte,
    val score: Int,
    val leftId: Short? = null,
    val rightId: Short? = null
)
