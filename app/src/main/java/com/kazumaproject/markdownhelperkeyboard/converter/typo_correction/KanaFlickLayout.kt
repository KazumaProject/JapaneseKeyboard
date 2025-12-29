package com.kazumaproject.markdownhelperkeyboard.converter.typo_correction

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

        KeyGroup.WA to KeyPos(0, 3),
    )

    // 各キーグループ内の5方向（CENTER/LEFT/UP/RIGHT/DOWN）
    // ※必要なら拗音/濁点/半濁点は別カテゴリで拡張可能
    private val table: Map<KeyGroup, Map<FlickDir, Char>> = mapOf(
        KeyGroup.A to mapOf(
            FlickDir.CENTER to 'あ',
            FlickDir.LEFT to 'い',
            FlickDir.UP to 'う',
            FlickDir.RIGHT to 'え',
            FlickDir.DOWN to 'お'
        ),
        KeyGroup.KA to mapOf(
            FlickDir.CENTER to 'か',
            FlickDir.LEFT to 'き',
            FlickDir.UP to 'く',
            FlickDir.RIGHT to 'け',
            FlickDir.DOWN to 'こ'
        ),
        KeyGroup.SA to mapOf(
            FlickDir.CENTER to 'さ',
            FlickDir.LEFT to 'し',
            FlickDir.UP to 'す',
            FlickDir.RIGHT to 'せ',
            FlickDir.DOWN to 'そ'
        ),
        KeyGroup.TA to mapOf(
            FlickDir.CENTER to 'た',
            FlickDir.LEFT to 'ち',
            FlickDir.UP to 'つ',
            FlickDir.RIGHT to 'て',
            FlickDir.DOWN to 'と'
        ),
        KeyGroup.NA to mapOf(
            FlickDir.CENTER to 'な',
            FlickDir.LEFT to 'に',
            FlickDir.UP to 'ぬ',
            FlickDir.RIGHT to 'ね',
            FlickDir.DOWN to 'の'
        ),
        KeyGroup.HA to mapOf(
            FlickDir.CENTER to 'は',
            FlickDir.LEFT to 'ひ',
            FlickDir.UP to 'ふ',
            FlickDir.RIGHT to 'へ',
            FlickDir.DOWN to 'ほ'
        ),
        KeyGroup.MA to mapOf(
            FlickDir.CENTER to 'ま',
            FlickDir.LEFT to 'み',
            FlickDir.UP to 'む',
            FlickDir.RIGHT to 'め',
            FlickDir.DOWN to 'も'
        ),
        KeyGroup.YA to mapOf(
            FlickDir.CENTER to 'や',
            FlickDir.LEFT to '（',
            FlickDir.UP to 'ゆ',
            FlickDir.RIGHT to '）',
            FlickDir.DOWN to 'よ'
            // LEFT/RIGHT を実運用の「ゃ/ゅ/ょ」等に合わせたいならここを差し替え
        ),
        KeyGroup.RA to mapOf(
            FlickDir.CENTER to 'ら',
            FlickDir.LEFT to 'り',
            FlickDir.UP to 'る',
            FlickDir.RIGHT to 'れ',
            FlickDir.DOWN to 'ろ'
        ),
        KeyGroup.WA to mapOf(
            FlickDir.CENTER to 'わ',
            FlickDir.LEFT to 'を',
            FlickDir.UP to 'ん',
            FlickDir.RIGHT to 'ー',
            FlickDir.DOWN to '〜'
            // ここもあなたの配列に合わせて調整
        ),
    )

    // 逆引き: ひらがな -> (キーグループ,方向)
    private val reverse: Map<Char, KanaKey> = buildMap {
        for ((g, m) in table) for ((d, ch) in m) put(ch, KanaKey(g, d))
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
