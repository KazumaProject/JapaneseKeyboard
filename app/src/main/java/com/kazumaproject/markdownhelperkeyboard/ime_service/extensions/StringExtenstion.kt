package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

fun String.correctReading(): Pair<String, String> {
    val readingCorrectionString = this.split("\t")
    val readingCorrectionTango = readingCorrectionString[0]
    val readingCorrectionCorrectYomi = readingCorrectionString[1]
    return Pair(readingCorrectionTango, readingCorrectionCorrectYomi)
}

fun List<String>.sortByFaceEmojiFirst(): List<String> {
    val isFaceEmoji: (String) -> Boolean = { emoji ->
        val codePoint = emoji.codePointAt(0)
        codePoint in 0x1F600..0x1F64F
    }

    return this.sortedWith(compareBy(
        { !isFaceEmoji(it) },
        { it == "😈" },
        { it }
    ))
}
