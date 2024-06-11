package com.kazumaproject

import com.kazumaproject.graph.Node

object Other {
    const val NUM_OF_CONNECTION_ID = 2662
    val BOS = Node(
        l = 0,
        r = 0,
        score = 0,
        f = 0,
        g = 0,
        tango = "BOS",
        len = 0,
        0,
    )

    val MAPPING_COMMON_PREFIX_SEARCH = mapOf(
        'か' to 'が', 'き' to 'ぎ', 'く' to 'ぐ', 'け' to 'げ', 'こ' to 'ご',
        'さ' to 'ざ', 'し' to 'じ', 'す' to 'ず', 'せ' to 'ぜ', 'そ' to 'ぞ',
        'た' to 'だ', 'ち' to 'ぢ', 'つ' to 'づ', 'て' to 'で', 'と' to 'ど',
        'は' to 'ば', 'ひ' to 'び', 'ふ' to 'ぶ', 'へ' to 'べ', 'ほ' to 'ぼ',
        'や' to 'ゃ', 'ゆ' to 'ゅ', 'よ' to 'ょ'
    )

}