package com.kazumaproject.markdownhelperkeyboard.converter

/** Fixed ten-key English labels, independently specified from the conversion implementation. */
internal object IndependentLiteralCandidates {
    private val labels = mapOf(
        'あ' to '@', 'い' to '#', 'う' to '/', 'え' to '_', 'お' to '1',
        'か' to 'a', 'き' to 'b', 'く' to 'c', 'こ' to '2',
        'さ' to 'd', 'し' to 'e', 'す' to 'f', 'そ' to '3',
        'た' to 'g', 'ち' to 'h', 'つ' to 'i', 'と' to '4',
        'な' to 'j', 'に' to 'k', 'ぬ' to 'l', 'の' to '5',
        'は' to 'm', 'ひ' to 'n', 'ふ' to 'o', 'ほ' to '6',
        'ま' to 'p', 'み' to 'q', 'む' to 'r', 'め' to 's', 'も' to '7',
        'や' to 't', 'ゆ' to 'v', 'よ' to '8',
        'ら' to 'w', 'り' to 'x', 'る' to 'y', 'れ' to 'z', 'ろ' to '9',
        'わ' to '\'', 'を' to '"', 'ん' to '(', 'ー' to ')', '〜' to '0',
        '、' to ',', '。' to '.', '？' to '?', '！' to '!',
    )

    fun englishKeyForms(input: String): Set<String> {
        val mapped = input.map { labels[it] ?: it }.joinToString("")
        return setOf(mapped, mapped.replaceFirstChar { it.uppercaseChar() }, mapped.uppercase())
    }
}
