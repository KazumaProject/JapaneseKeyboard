package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

import android.view.inputmethod.InputConnection
import timber.log.Timber
import java.text.BreakIterator

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

/**
 * 絵文字などを含む文字列を「グラフェム単位」で逆順にする拡張関数
 */
fun String.reversePreservingGraphemes(): String {
    // 文字単位（Grapheme Cluster）で区切る BreakIterator を用意
    val breaker = BreakIterator.getCharacterInstance()
    breaker.setText(this)

    val result = StringBuilder(this.length)
    var end = breaker.last()
    var start = breaker.previous()

    while (start != BreakIterator.DONE) {
        // start..end で「絵文字ひとまとまり」や通常の一文字を切り出して追加
        result.append(this.substring(start, end))
        end = start
        start = breaker.previous()
    }
    return result.toString()
}

fun debugPrintCodePoints(text: String) {
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        // 16進数で出してみれば、サロゲートペアが壊れていないかも確認できる
        Timber.d("index=%d, codePoint=0x%04X (%s)", i, cp, Character.charCount(cp).let { if (it == 2) "サロゲートペア" else "単一" })
        i += Character.charCount(cp)
    }
}

fun getLastCharacterAsString(ic: InputConnection): String {
    // Try to read two code units, so that if the last character is a surrogate pair
    // we can grab both halves. If it’s just a BMP character, we'll end up with length=1.
    val twoChars = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
    if (twoChars.length >= 2) {
        val lastIndex = twoChars.length - 1
        val low = twoChars[lastIndex]
        val high = twoChars[lastIndex - 1]
        // if it really is a valid surrogate pair:
        if (Character.isLowSurrogate(low) && Character.isHighSurrogate(high)) {
            // return exactly those two code units as a String
            return twoChars.substring(lastIndex - 1, lastIndex + 1)
        }
    }
    // otherwise, just return the single code unit (could be a normal letter or an unpaired surrogate)
    return twoChars.takeLast(1)
}
