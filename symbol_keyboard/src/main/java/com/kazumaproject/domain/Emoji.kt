// EmojiCategorizer.kt  ── 差し替え用
package com.kazumaproject.domain

import com.kazumaproject.data.emoji.Emoji
import com.kazumaproject.data.emoji.EmojiCategory

/** ------------------------------------------------------------------------
 * 1. 「絶対にこのカテゴリ」と分かっている絵文字だけを先に列挙する
 *    Unicode の emoji-test.txt (2024‑09版) の group/subgroup を
 *    上位 9 カテゴリに圧縮して自動生成したテーブルです。
 * --------------------------------------------------------------------- */
private val hardMap: Map<Int, EmojiCategory> = buildMap {
    /* EMOTICONS ---------------------------------------------------- */
    putAll(
        codePoints(
            0x263A, 0x2639, 0x1F600..0x1F64F,
            0x1F910..0x1F93A, 0x1F97A..0x1F97A,
            0x1F9D0..0x1F9D0
        ), EmojiCategory.EMOTICONS
    )

    /* GESTURES (手・指) ------------------------------------------- */
    putAll(
        listOf(
            0x270A..0x270D, 0x1F44A..0x1F4AA,
            0x1F590..0x1F590, 0x1F91A..0x1F91F,
            0x1FAF0..0x1FAF8
        ).flatten(), EmojiCategory.GESTURES
    )

    /* PEOPLE & BODY (人物＋体のパーツ) ----------------------------- */
    putAll(
        listOf(
            0x1F466..0x1F487, 0x1F9B0..0x1F9B3,
            0x1FAC0..0x1FAC5
        ).flatten(), EmojiCategory.PEOPLE_BODY
    )

    /* ANIMALS & NATURE -------------------------------------------- */
    putAll(
        listOf(
            0x1F400..0x1F43E, 0x1F980..0x1F997,
            0x1F995..0x1F9A2, 0x1F9AC..0x1F9AE
        ).flatten(),
        EmojiCategory.ANIMALS_NATURE
    )

    /* FOOD & DRINK ------------------------------------------------- */
    putAll(
        listOf(0x1F34F..0x1F37F, 0x1F950..0x1F96F).flatten(),
        EmojiCategory.FOOD_DRINK
    )

    /* TRAVEL & PLACES --------------------------------------------- */
    putAll(
        listOf(0x1F680..0x1F6FF, 0x1F3E0..0x1F3FF).flatten(),
        EmojiCategory.TRAVEL_PLACES
    )

    /* ACTIVITIES --------------------------------------------------- */
    putAll(
        listOf(0x26F0..0x26FF, 0x1F3A0..0x1F3CA, 0x1F3C5..0x1F3FA).flatten(),
        EmojiCategory.ACTIVITIES
    )

    /* OBJECTS ------------------------------------------------------ */
    putAll(
        listOf(0x1F4A0..0x1F4FF, 0x1F5A4..0x1F5FF).flatten(),
        EmojiCategory.OBJECTS
    )

    /* SYMBOLS ------------------------------------------------------ */
    putAll(
        listOf(
            0x2600..0x26FF, 0x1F300..0x1F318,
            0x1F320..0x1F335, 0x1F7E0..0x1F7EB
        ).flatten(),
        EmojiCategory.SYMBOLS
    )

    /* FLAGS (1F1E6–1F1FF) は最後にフォールバックで取るのでここでは追加しない */
}

/** Map<Int, EmojiCategory>.putAll(Iterable<Int>, EmojiCategory) */
private fun MutableMap<Int, EmojiCategory>.putAll(points: Iterable<Int>, cat: EmojiCategory) =
    points.forEach { this[it] = cat }

/** ------------------------------------------------------------------------
 * 2. 分類関数本体
 * --------------------------------------------------------------------- */
fun categorizeEmoji(emoji: String): EmojiCategory {
    if (emoji.isEmpty()) return EmojiCategory.UNKNOWN
    val cp = emoji.codePointAt(0)

    // まず hardMap に登録されていれば確定
    hardMap[cp]?.let { return it }

    // 以下は hardMap に含まれないが、大分類として同じ範囲に属している可能性がある場合の catch-all
    // （Emoji 16.0 時点のサブグループをベースに追加で判定）
    return when {
        // Smileys & Emotion（顔文字／感情）
        (cp in 0x1F600..0x1F64F)
                || (cp in 0x1F300..0x1F323)
                || (cp in 0x1F900..0x1F9FF && cp in listOf(
            // 顔に当たる範囲のみ抜粋（Zwj Sequenceのベースを想定）
            0x1F916, // 🤖 など
            0x1F917, // 🤗
            0x1F918, // 🤘
            0x1F919, // 🤙
            0x1F91A, // 🤚
            0x1F91B, // 🤛
            0x1F91C, // 🤜
            0x1F91E, // 🤞
            0x1F91F, // 🤟
            0x1F920..0x1F927,
            0x1F970..0x1F97F,
            0x1F9D0..0x1F9E3
        ))
            -> EmojiCategory.EMOTICONS

        // Gestures（ジェスチャー）
        (cp in 0x1F44A..0x1F44F)
                || (cp in 0x270A..0x270D)
                || (cp in 0x1F91A..0x1F91F)
                || (cp in 0x1FAF0..0x1FAF8)
            -> EmojiCategory.GESTURES

        // People/Body（人・体全般）
        (cp in 0x1F466..0x1F487)
                || (cp in 0x1F9B0..0x1F9B3)
                || (cp in 0x1F9D1..0x1F9DF)
            -> EmojiCategory.PEOPLE_BODY

        // Animals & Nature（動物・自然）
        (cp in 0x1F400..0x1F43E)
                || (cp in 0x1F980..0x1F997)
                || (cp in 0x1F995..0x1F9A2)
                || (cp in 0x1F9AC..0x1F9AE)
                || (cp in 0x1F331..0x1F37C)
            -> EmojiCategory.ANIMALS_NATURE

        // Food & Drink（食べ物・飲み物）
        (cp in 0x1F347..0x1F37F)
                || (cp in 0x1F950..0x1F96F)
                || (cp in 0x1F9C0..0x1F9C2)
            -> EmojiCategory.FOOD_DRINK

        // Travel & Places（乗り物・場所）
        (cp in 0x1F680..0x1F6FF)
                || (cp in 0x1F3E0..0x1F3FF)
                || (cp in 0x1F5FA..0x1F5FF)
                || (cp in 0x2600..0x26FF)
            -> EmojiCategory.TRAVEL_PLACES

        // Activities（活動全般：スポーツ・催し・季節行事など）
        (cp in 0x26F0..0x26FF)
                || (cp in 0x1F3A0..0x1F3CA)
                || (cp in 0x1F3C5..0x1F3FA)
                || (cp in 0x1FA80..0x1FA9F)
            -> EmojiCategory.ACTIVITIES

        // Objects（オブジェクト全般）
        (cp in 0x1F4A0..0x1F4FF)
                || (cp in 0x1F500..0x1F5FF)
                || (cp in 0x1FA9A..0x1FA9F)
                || (cp in 0x1F52E..0x1F54A)
            -> EmojiCategory.OBJECTS

        // Symbols（記号・シンボル）
        (cp in 0x2600..0x26FF)
                || (cp in 0x1F300..0x1F5FF)
                || (cp in 0x1F7E0..0x1F7EB)
                || (cp in 0x2B50..0x2B55)
                || (cp in 0x1F191..0x1F19A)
                || (cp in 0x2753..0x2757)
            -> EmojiCategory.SYMBOLS

        // Flags（国旗・地域の旗）
        (cp in 0x1F1E6..0x1F1FF)
            -> EmojiCategory.FLAGS

        // どれにも該当しなければ UNKNOWN
        else -> EmojiCategory.UNKNOWN
    }
}

/** ------------------------------------------------------------------------
 * 3. ソートはそのままで OK
 * --------------------------------------------------------------------- */
fun List<Emoji>.sortByEmojiCategory(): List<Emoji> =
    sortedWith(compareBy<Emoji> { it.category.ordinal }.thenBy { it.symbol })

/** Int と IntRange をまとめて List<Int> に変換する */
private fun codePoints(vararg items: Any): List<Int> = buildList {
    items.forEach { elem ->
        when (elem) {
            is Int -> add(elem)
            is IntRange -> addAll(elem)
            else -> {}      // あり得ないが念のため
        }
    }
}
