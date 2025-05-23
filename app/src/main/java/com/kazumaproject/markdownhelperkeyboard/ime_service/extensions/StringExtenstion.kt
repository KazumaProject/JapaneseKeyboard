package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

fun String.correctReading(): Pair<String, String> {
    val readingCorrectionString = this.split("\t")
    val readingCorrectionTango = readingCorrectionString[0]
    val readingCorrectionCorrectYomi = readingCorrectionString[1]
    return Pair(readingCorrectionTango, readingCorrectionCorrectYomi)
}

fun List<String>.sortByEmojiCategory(): List<String> {
    // Define emoji categories based on Unicode ranges and specific code points
    val isFaceEmoji: (String) -> Boolean = { emoji ->
        val codePoint = emoji.codePointAt(0)
        (codePoint in 0x1F600..0x1F64F && emoji != "😈") || // General face emoji range
                codePoint in listOf(
            0x263A,
            0x2639,
            0x1F910,
            0x1F914,
            0x1F911,
            0x1F925,
            0x1F927,
            0x1F92C,
            0x1F92D,
            0x1F912,
            0x1F920,
            0x1F917,
            0x1F922,
            0x1F602,
            0x1F970,
            0x1F971,
            0x1F972,
            0x1F973,
            0x1F974,
            0x1F975,
            0x1F976,
            0x1F978,
            0x1F979,
            0x1F97A,
            0x1FAE0,
            0x1FAE1,
            0x1FAE2,
            0x1FAE3,
            0x1FAE4,
            0x1FAE8,
            0x1F913,
            0x1F915,
            0x1F916,
            0x1F921,
            0x1F923,
            0x1F924,
            0x1F928,
            0x1F929,
            0x1F92A,
            0x1F92B,
            0x1F92E,
            0x1F92F
        )
    }

    val isFingerEmoji: (String) -> Boolean = { emoji ->
        val codePoint = emoji.codePointAt(0)
        // Include specific finger gestures along with general finger emojis
        (codePoint in 0x1F590..0x1F596 || codePoint in 0x1F918..0x1F91F || codePoint == 0x1F44D || // Basic fingers including 👍 (0x1F44D)
                codePoint in listOf(
            0x1F90C,
            0x1F90F,
            0x1F446,
            0x1F447,
            0x1F448,
            0x1F449,
            0x1F44A,
            0x1F44B,
            0x1F44C,
            0x1F44E,
            0x1F44F,
            0x1F450,
            0x270A,
            0x1F590,
            0x270C,
            0x270D,
            0x1F4AA,
            0x1FAF0,
            0x1FAF1,
            0x1FAF2,
            0x1FAF3,
            0x1FAF4,
            0x1FAF5,
            0x1FAF6,
            0x1FAF7,
            0x1FAF8,
            0x1F91A,
            0x261D,
            0x1F485,
            0x1F933,
            0x1F9BE,
            0x1F9BF,
            0x1F9B5,
            0x1F9B6,
            0x1F442,
            0x1F9BB,
            0x1F443,
            0x1FAC0,
            0x1FAC1,
            0x1F9B7,
            0x1F440,
            0x1F441,
            0x1F445,
            0x1F444,
            0x1FAE6
        ))
    }

    val isPeopleEmoji: (String) -> Boolean = { emoji ->
        val codePoint = emoji.codePointAt(0)
        codePoint in 0x1F466..0x1F487 || codePoint in 0x1F9D0..0x1F9E6 // People emojis
    }

    val isAnimalEmoji: (String) -> Boolean = { emoji ->
        val codePoint = emoji.codePointAt(0)
        codePoint in 0x1F400..0x1F43E || codePoint in 0x1F98A..0x1F9A2 // Animal emojis
    }

    val isFlagEmoji: (String) -> Boolean = { emoji ->
        val codePoint = emoji.codePointAt(0)
        codePoint in 0x1F1E6..0x1F1FF // Flag emojis
    }

    return this.sortedWith(compareBy(
        { !isFaceEmoji(it) },               // Face emojis first, excluding 😈
        { it == "😷" },                     // Place 😷 before ☺️ and ☹️
        { it in listOf("☺️", "☹️") },       // Place ☺️ and ☹️ right after 😷
        { !isFingerEmoji(it) },             // Fingers, including 👍, ✋, 🤚, ☝️, and other gestures, excluding 🖕
        { !isPeopleEmoji(it) },             // Then people
        { !isAnimalEmoji(it) },             // Then animals
        { !isFlagEmoji(it) },               // Finally flags
        { it == "😈" || it == "🖕" },       // Place 😈 and 🖕 at the end
        { it }                               // Lexicographical order within each category
    ))
}

fun Char.isEnglishLetter(): Boolean {
    return this in 'a'..'z' || this in 'A'..'Z'
}

fun String.isAllEnglishLetters(): Boolean =
    isNotEmpty() && all { it.isEnglishLetter() }
