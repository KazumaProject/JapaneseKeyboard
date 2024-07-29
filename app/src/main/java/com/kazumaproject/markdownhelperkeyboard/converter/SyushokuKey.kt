package com.kazumaproject.markdownhelperkeyboard.converter

val shushokuKeys = mapOf(
    'あ' to listOf('ぁ'),
    'い' to listOf('ぃ'),
    'う' to listOf('ぅ'),
    'え' to listOf('ぇ'),
    'お' to listOf('ぉ'),
    'か' to listOf('が'),
    'き' to listOf('ぎ'),
    'く' to listOf('ぐ'),
    'け' to listOf('げ'),
    'こ' to listOf('ご'),
    'さ' to listOf('ざ'),
    'し' to listOf('じ'),
    'す' to listOf('ず'),
    'せ' to listOf('ぜ'),
    'そ' to listOf('ぞ'),
    'た' to listOf('だ'),
    'ち' to listOf('ぢ'),
    'つ' to listOf('づ','っ'),
    'て' to listOf('で'),
    'と' to listOf('ど'),
    'は' to listOf('ば','ぱ'),
    'ひ' to listOf('び','ぴ'),
    'ふ' to listOf('ぶ','ぷ'),
    'へ' to listOf('べ','ぺ'),
    'ほ' to listOf('ぼ','ぽ'),
    'や' to listOf('ゃ'),
    'ゆ' to listOf('ゅ'),
    'よ' to listOf('ょ'),
    'わ' to listOf('ゎ'),
)

fun generateCombinations(input: String): List<String> {
    val results = mutableListOf("")
    for (char in input) {
        val substitutions = shushokuKeys[char]?.plus(char) ?: listOf(char)
        val newResults = mutableListOf<String>()
        for (result in results) {
            for (substitution in substitutions) {
                newResults.add(result + substitution)
            }
        }
        results.clear()
        results.addAll(newResults)
    }
    return results
}