package com.kazumaproject.core.domain.extensions

fun Char.asFullWidth(): Char =
    if (code in 0x21..0x7E) (code + 0xFEE0).toChar() else this
