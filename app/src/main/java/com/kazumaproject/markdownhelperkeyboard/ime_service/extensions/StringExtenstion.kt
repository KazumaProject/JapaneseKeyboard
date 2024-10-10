package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

fun String.correctReading(): Pair<String, String>{
    val readingCorrectionString = this.split("\t")
    val readingCorrectionTango = readingCorrectionString[0]
    val readingCorrectionCorrectYomi = readingCorrectionString[1]
    return Pair(readingCorrectionTango, readingCorrectionCorrectYomi)
}