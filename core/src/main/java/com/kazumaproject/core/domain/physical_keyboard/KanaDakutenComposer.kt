package com.kazumaproject.core.domain.physical_keyboard

object KanaDakutenComposer {
    fun append(current: String, next: String): String {
        if (next != "゛" && next != "゜") return current + next
        val last = current.lastOrNull() ?: return current + next
        val composed = composeMap["$last$next"] ?: return current + next
        return current.dropLast(1) + composed
    }

    private val composeMap = mapOf(
        "か゛" to "が",
        "き゛" to "ぎ",
        "く゛" to "ぐ",
        "け゛" to "げ",
        "こ゛" to "ご",
        "さ゛" to "ざ",
        "し゛" to "じ",
        "す゛" to "ず",
        "せ゛" to "ぜ",
        "そ゛" to "ぞ",
        "た゛" to "だ",
        "ち゛" to "ぢ",
        "つ゛" to "づ",
        "て゛" to "で",
        "と゛" to "ど",
        "は゛" to "ば",
        "ひ゛" to "び",
        "ふ゛" to "ぶ",
        "へ゛" to "べ",
        "ほ゛" to "ぼ",
        "は゜" to "ぱ",
        "ひ゜" to "ぴ",
        "ふ゜" to "ぷ",
        "へ゜" to "ぺ",
        "ほ゜" to "ぽ",
        "う゛" to "ゔ",
    )
}
