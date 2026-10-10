package com.kazumaproject.markdownhelperkeyboard.converter.graph

enum class KeyGroup { A, KA, SA, TA, NA, HA, MA, YA, RA, WA }

enum class FlickDir { CENTER, LEFT, UP, RIGHT, DOWN } // a,i,u,e,o

data class KeyPos(val x: Int, val y: Int)
data class KanaKey(val group: KeyGroup, val dir: FlickDir)


object KanaFlickLayout {

    // 12キーの代表的な配置
    private val pos = mapOf(
        KeyGroup.A to KeyPos(0, 0),
        KeyGroup.KA to KeyPos(1, 0),
        KeyGroup.SA to KeyPos(2, 0),

        KeyGroup.TA to KeyPos(0, 1),
        KeyGroup.NA to KeyPos(1, 1),
        KeyGroup.HA to KeyPos(2, 1),

        KeyGroup.MA to KeyPos(0, 2),
        KeyGroup.YA to KeyPos(1, 2),
        KeyGroup.RA to KeyPos(2, 2),

        KeyGroup.WA to KeyPos(1, 3),
    )

    // 各キーグループ内の5方向（CENTER/LEFT/UP/RIGHT/DOWN）
    // ※必要なら拗音/濁点/半濁点は別カテゴリで拡張可能
    private val table: Map<KeyGroup, Map<FlickDir, Char>> = listOf(
        KeyGroup.A to com.kazumaproject.core.domain.key.KeyInfo.KeyAJapanese,
        KeyGroup.KA to com.kazumaproject.core.domain.key.KeyInfo.KeyKAJapanese,
        KeyGroup.SA to com.kazumaproject.core.domain.key.KeyInfo.KeySAJapanese,
        KeyGroup.TA to com.kazumaproject.core.domain.key.KeyInfo.KeyTAJapanese,
        KeyGroup.NA to com.kazumaproject.core.domain.key.KeyInfo.KeyNAJapanese,
        KeyGroup.HA to com.kazumaproject.core.domain.key.KeyInfo.KeyHAJapanese,
        KeyGroup.MA to com.kazumaproject.core.domain.key.KeyInfo.KeyMAJapanese,
        KeyGroup.YA to com.kazumaproject.core.domain.key.KeyInfo.KeyYAJapanese,
        KeyGroup.RA to com.kazumaproject.core.domain.key.KeyInfo.KeyRAJapanese,
        KeyGroup.WA to com.kazumaproject.core.domain.key.KeyInfo.KeyWAJapanese,
    ).associate { (group, info) ->
        group to mapOf(
            FlickDir.CENTER to info.tap, FlickDir.LEFT to info.flickLeft,
            FlickDir.UP to info.flickTop, FlickDir.RIGHT to info.flickRight,
            FlickDir.DOWN to info.flickBottom,
        ).mapNotNull { (direction, value) -> value?.let { direction to it } }.toMap()
    }

    // 逆引き: ひらがな -> (キーグループ,方向)
    private val reverse: Map<Char, KanaKey> = buildMap {
        for ((g, m) in table) for ((d, ch) in m) put(ch, KanaKey(g, d))
    }

    fun baseChar(ch: Char): Char = when (ch) {
        'ぁ' -> 'あ'; 'ぃ' -> 'い'; 'ぅ', 'ゔ' -> 'う'; 'ぇ' -> 'え'; 'ぉ' -> 'お'
        'っ' -> 'つ'; 'ゃ' -> 'や'; 'ゅ' -> 'ゆ'; 'ょ' -> 'よ'; 'ゎ' -> 'わ'
        'が', 'ぎ', 'ぐ', 'げ', 'ご', 'ざ', 'じ', 'ず', 'ぜ', 'ぞ',
        'だ', 'ぢ', 'づ', 'で', 'ど' -> ch - 1
        'ば', 'び', 'ぶ', 'べ', 'ぼ' -> ch - 1
        'ぱ', 'ぴ', 'ぷ', 'ぺ', 'ぽ' -> ch - 2
        else -> ch
    }

    fun modifierOf(ch: Char): Int = when (ch) {
        'ぁ', 'ぃ', 'ぅ', 'ぇ', 'ぉ', 'っ', 'ゃ', 'ゅ', 'ょ', 'ゎ' -> 1
        'ぱ', 'ぴ', 'ぷ', 'ぺ', 'ぽ' -> 3
        else -> if (ch != baseChar(ch)) 2 else 0
    }

    fun keyOf(ch: Char): KanaKey? = reverse[ch]
    fun charOf(group: KeyGroup, dir: FlickDir): Char? = table[group]?.get(dir)
    fun posOf(group: KeyGroup): KeyPos? = pos[group]

    fun manhattan(a: KeyGroup, b: KeyGroup): Int {
        val pa = posOf(a) ?: return Int.MAX_VALUE
        val pb = posOf(b) ?: return Int.MAX_VALUE
        return kotlin.math.abs(pa.x - pb.x) + kotlin.math.abs(pa.y - pb.y)
    }

    fun allGroups(): List<KeyGroup> = pos.keys.toList()
}
