package com.kazumaproject.markdownhelperkeyboard.converter.candidate

/**
 * @see 1:NBest 2:Part of letters 3:Hirakana 4:Katakana
 **/
data class Candidate (
    val string: String,
    val type: Byte,
    val length: UByte
)