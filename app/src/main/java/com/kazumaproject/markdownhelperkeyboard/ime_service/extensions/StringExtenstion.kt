package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

fun String.correctReading(): Pair<String, String> {
    val readingCorrectionString = this.split("\t")
    val readingCorrectionTango = readingCorrectionString[0]
    val readingCorrectionCorrectYomi = readingCorrectionString[1]
    return Pair(readingCorrectionTango, readingCorrectionCorrectYomi)
}

fun Char.isEnglishLetter(): Boolean {
    return this in 'a'..'z' || this in 'A'..'Z'
}

fun String.isAllEnglishLetters(): Boolean =
    isNotEmpty() && all { it.isEnglishLetter() }

fun String.isEmoji(): Boolean {
    if (this.isEmpty()) return false
    val codePoint = this.codePointAt(0)

    return when (codePoint) {
        in 0x1F600..0x1F64F, // Emoticons
        in 0x1F300..0x1F5FF, // Misc Symbols and Pictographs
        in 0x1F680..0x1F6FF, // Transport and Map
        in 0x1F1E6..0x1F1FF, // Flags
        in 0x2600..0x26FF,   // Misc symbols
        in 0x2700..0x27BF,   // Dingbats
        in 0x1F900..0x1F9FF, // Supplemental Symbols and Pictographs
        in 0x1FA70..0x1FAFF, // Symbols and Pictographs Extended-A
        in 0x1F700..0x1F77F, // Alchemical Symbols
        in 0x1F780..0x1F7FF, // Geometric Shapes Extended
        in 0x1F800..0x1F8FF  // Supplemental Arrows-C
            -> true
        else -> false
    }
}
