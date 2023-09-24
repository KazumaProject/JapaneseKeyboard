package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

fun Int.convertUnicode(): String{
    return String(Character.toChars(this))
}