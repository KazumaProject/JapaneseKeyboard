package com.kazumaproject.domain

import com.kazumaproject.data.emoji.Emoji
import com.kazumaproject.data.emoji.EmojiCategory
import com.kazumaproject.data.emoticon.EmoticonCategory
import com.kazumaproject.data.symbol.SymbolCategory

private val hardMap: Map<Int, EmojiCategory> = buildMap {
    putAll(
        codePoints(
            0x263A, 0x2639,
            0x1F600..0x1F64F, 0x1F910..0x1F93A, 0x1F97A..0x1F97A, 0x1F9D0..0x1F9D0,
            0x1F971, 0x1F974, 0x1F972, 0x1F973, 0x1F975, 0x1F976, 0x1F978, 0x1F979,
            0x1FAE0..0x1FAE5, 0x1FAE8
        ),
        EmojiCategory.EMOTICONS
    )

    putAll(
        codePoints(
            0x270A..0x270D,
            0x1F44A..0x1F4AA,
            0x1F590..0x1F590,
            0x1F91A..0x1F91F,
            0x1FAF0..0x1FAF8,
            0x1F64C,
            0x1F64F,
            0x1F919,
            0x1F918,
            0x1F933,
            0x1F596,
            0x1F595,
            0x1F440,
            0x1F441,
            0x1F442,
            0x1F443,
            0x1F444,
            0x1F445,
            0x1F446,
            0x1F447,
            0x1F448,
            0x1F449,
            0x261D,
            0x1F90C,
            0x1F90F
        ),
        EmojiCategory.GESTURES
    )

    putAll(
        codePoints(
            0x1F319..0x1F31F,
            0x1F466..0x1F487,
            0x1F9B0..0x1F9B3,
            0x1FAC0..0x1FAC5,
            0x1F385,
            0x1F977,
            0x1F9B5,
            0x1F9B6,
            0x1F9B8,
            0x1F9B9,
            0x1F9BF,
            0x1F9BE,
            0x1F9CD,
            0x1F9CE,
            0x1F9CF,
            0x1FAE6,
            0x1F9E0
        ),
        EmojiCategory.PEOPLE_BODY
    )

    putAll(
        codePoints(
            0x1F43F,
            0x1F9E3,
            0x1F9E5,
            0x1F9A6,
            0x1F9A4,
            0x1F9A7,
            0x1F9A8,
            0x1F9A9,
            0x1F9AB,
            0x1F9AA,
            0x1FAB0,
            0x1FAB1,
            0x1FAB2,
            0x1FAB3,
            0x1FAB4,
            0x1FAB5,
            0x1FAB6,
            0x1FAB7,
            0x1FAB8,
            0x1FABB,
            0x1FABC,
            0x1FABD,
            0x1FABF,
            0x1FACE,
            0x1FACF,
            0x1FAD0,
            0x1FAD1,
            0x1FAD2,
            0x1FAD3,
            0x1FADA,
            0x1FADB,
            0x1FAD8
        ),
        EmojiCategory.ANIMALS_NATURE
    )

    putAll(
        codePoints(0x1F34F..0x1F37F, 0x1F950..0x1F96F),
        EmojiCategory.FOOD_DRINK
    )

    putAll(
        codePoints(0x1F680..0x1F6FF, 0x1F3E0..0x1F3FF),
        EmojiCategory.TRAVEL_PLACES
    )

    putAll(
        codePoints(0x26F0..0x26FF, 0x1F3A0..0x1F3CA, 0x1F3C5..0x1F3FA),
        EmojiCategory.ACTIVITIES
    )

    putAll(
        codePoints(0x1F4A0..0x1F4FF, 0x1F5A4..0x1F5FF),
        EmojiCategory.OBJECTS
    )

    putAll(
        codePoints(0x2600..0x26FF, 0x1F300..0x1F318, 0x1F320..0x1F335, 0x1F7E0..0x1F7EB),
        EmojiCategory.SYMBOLS
    )
}

private fun MutableMap<Int, EmojiCategory>.putAll(points: Iterable<Int>, cat: EmojiCategory) =
    points.forEach { this[it] = cat }

fun categorizeEmoji(emoji: String): EmojiCategory {
    if (emoji.isEmpty()) return EmojiCategory.UNKNOWN
    val cp = emoji.codePointAt(0)
    hardMap[cp]?.let { return it }
    return when {
        (cp in 0x1F600..0x1F64F)
                || (cp in 0x1F300..0x1F323)
                || (cp in 0x1F900..0x1F9FF && cp in listOf(
            0x1F916, 0x1F917, 0x1F918, 0x1F919, 0x1F91A, 0x1F91B,
            0x1F91C, 0x1F91E, 0x1F91F, 0x1F920..0x1F927,
            0x1F970..0x1F97F, 0x1F9D0..0x1F9E3
        ))
            -> EmojiCategory.EMOTICONS

        (cp in 0x1F44A..0x1F44F)
                || (cp in 0x270A..0x270D)
                || (cp in 0x1F91A..0x1F91F)
                || (cp in 0x1FAF0..0x1FAF8)
            -> EmojiCategory.GESTURES

        (cp in 0x1F466..0x1F487)
                || (cp in 0x1F9B0..0x1F9B3)
                || (cp in 0x1F9D1..0x1F9DF)
            -> EmojiCategory.PEOPLE_BODY

        (cp in 0x1F400..0x1F43E)
                || (cp in 0x1F980..0x1F997)
                || (cp in 0x1F995..0x1F9A2)
                || (cp in 0x1F9AC..0x1F9AE)
                || (cp in 0x1F331..0x1F37C)
            -> EmojiCategory.ANIMALS_NATURE

        (cp in 0x1F347..0x1F37F)
                || (cp in 0x1F950..0x1F96F)
                || (cp in 0x1F9C0..0x1F9C2)
            -> EmojiCategory.FOOD_DRINK

        (cp in 0x1F680..0x1F6FF)
                || (cp in 0x1F3E0..0x1F3FF)
                || (cp in 0x1F5FA..0x1F5FF)
                || (cp in 0x2600..0x26FF)
            -> EmojiCategory.TRAVEL_PLACES

        (cp in 0x26F0..0x26FF)
                || (cp in 0x1F3A0..0x1F3CA)
                || (cp in 0x1F3C5..0x1F3FA)
                || (cp in 0x1FA80..0x1FA9F)
            -> EmojiCategory.ACTIVITIES

        (cp in 0x1F4A0..0x1F4FF)
                || (cp in 0x1F500..0x1F5FF)
                || (cp in 0x1FA9A..0x1FA9F)
                || (cp in 0x1F52E..0x1F54A)
            -> EmojiCategory.OBJECTS

        (cp in 0x2600..0x26FF)
                || (cp in 0x1F300..0x1F5FF)
                || (cp in 0x1F7E0..0x1F7EB)
                || (cp in 0x2B50..0x2B55)
                || (cp in 0x1F191..0x1F19A)
                || (cp in 0x2753..0x2757)
            -> EmojiCategory.SYMBOLS

        (cp in 0x1F1E6..0x1F1FF)
            -> EmojiCategory.FLAGS

        else -> EmojiCategory.UNKNOWN
    }
}

private fun codePoints(vararg items: Any): List<Int> = buildList {
    items.forEach { elem ->
        when (elem) {
            is Int -> add(elem)
            is IntRange -> addAll(elem)
        }
    }
}

fun List<Emoji>.sortByEmojiCategory(): List<Emoji> =
    sortedWith(compareBy<Emoji> { it.category.ordinal }.thenBy { it.symbol })

private val emoticonCategoryMap = mapOf(
    "(๑•̀ㅁ•́๑)✧" to EmoticonCategory.SMILE,
    "(•ө•)♡" to EmoticonCategory.SMILE,
    "(๑´ڡ`๑)" to EmoticonCategory.SMILE,
    "(๑•̀ㅂ•́)و✧" to EmoticonCategory.SMILE,
    "•̀.̫•́✧" to EmoticonCategory.SMILE,
    "ʕ•̀ω•́ʔ✧" to EmoticonCategory.SMILE,
    "ʕ•ٹ•ʔ" to EmoticonCategory.SMILE,
    "ლ(´ڡ`ლ)" to EmoticonCategory.SMILE,
    "(･ิω･ิ)" to EmoticonCategory.SMILE,
    "٩(๑´3｀๑)۶" to EmoticonCategory.SMILE,
    "(ㆁωㆁ*)" to EmoticonCategory.SMILE,
    "(*´ڡ`●)" to EmoticonCategory.SMILE,
    "٩(♡ε♡ )۶" to EmoticonCategory.SMILE,
    "(*˘︶˘*).｡.:*♡" to EmoticonCategory.SMILE,
    "❤(ӦｖӦ｡)" to EmoticonCategory.SMILE,
    ":D" to EmoticonCategory.SMILE,
    ":)" to EmoticonCategory.SMILE,
    ";)" to EmoticonCategory.SMILE,
    ":-)" to EmoticonCategory.SMILE,
    "(●´ϖ`●)" to EmoticonCategory.SMILE,
    "＼(^o^)／" to EmoticonCategory.SMILE,
    "(^o^)" to EmoticonCategory.SMILE,
    "(^^)" to EmoticonCategory.SMILE,
    "(^^)/" to EmoticonCategory.SMILE,
    "(^^)v" to EmoticonCategory.SMILE,
    "(^_^)v" to EmoticonCategory.SMILE,
    "(*^^*)" to EmoticonCategory.SMILE,
    "^_^" to EmoticonCategory.SMILE,
    "(*´ω｀*)" to EmoticonCategory.SMILE,
    "(>ω<)" to EmoticonCategory.SMILE,
    "(・∀・)" to EmoticonCategory.SMILE,
    "(*^_^*)" to EmoticonCategory.SMILE,
    "ヾ(｡>﹏<｡)ﾉﾞ✧*。" to EmoticonCategory.SMILE,
    "(*>_<*)ﾉ" to EmoticonCategory.SMILE,
    "(｡>﹏<｡)" to EmoticonCategory.SMILE,
    "(*´∀｀)" to EmoticonCategory.SMILE,
    "(^_-)" to EmoticonCategory.SMILE,
    "( ´∀｀)" to EmoticonCategory.SMILE,
    "(≧∇≦)b" to EmoticonCategory.SMILE,
    "(≧▽≦)" to EmoticonCategory.SMILE,
    "(^^♪" to EmoticonCategory.SMILE,
    "(*^^)v" to EmoticonCategory.SMILE,
    "(^o^)v" to EmoticonCategory.SMILE,
    "ヽ(^o^)丿" to EmoticonCategory.SMILE,
    "(^_^)/" to EmoticonCategory.SMILE,
    "(^_-)-☆" to EmoticonCategory.SMILE,
    "(^o^)／" to EmoticonCategory.SMILE,
    "(*ﾟ∀ﾟ)" to EmoticonCategory.SMILE,
    "(*^。^*)" to EmoticonCategory.SMILE,
    "(^o^)丿" to EmoticonCategory.SMILE,
    "ヽ(^。^)ノ" to EmoticonCategory.SMILE,
    "(ﾉ´∀｀*)" to EmoticonCategory.SMILE,
    "(^.^)" to EmoticonCategory.SMILE,
    "(^O^)v" to EmoticonCategory.SMILE,
    "(￣ー￣)ﾆﾔﾘ" to EmoticonCategory.SMILE,
    "(^_^.)" to EmoticonCategory.SMILE,
    "(^・^)" to EmoticonCategory.SMILE,
    "(^^)/~~~" to EmoticonCategory.SMILE,
    "(^ム^)" to EmoticonCategory.SMILE,
    "(@^^)/~~~" to EmoticonCategory.SMILE,
    "(^.^)/~~~" to EmoticonCategory.SMILE,
    "（ ・∀・）" to EmoticonCategory.SMILE,
    "( •̀ㅁ•́;)" to EmoticonCategory.SWEAT,
    "(¯―¯٥)" to EmoticonCategory.SADNESS,
    "(´-﹏-`；)" to EmoticonCategory.SWEAT,
    "(ㆀ˘･з･˘)" to EmoticonCategory.SWEAT,
    "^^;" to EmoticonCategory.SWEAT,
    "(^_^;)" to EmoticonCategory.SWEAT,
    "(-_-;)" to EmoticonCategory.DISPLEASURE,
    "(~_~;)" to EmoticonCategory.DISPLEASURE,
    "^_^;" to EmoticonCategory.SWEAT,
    "(・・;)" to EmoticonCategory.SWEAT,
    "(^^ゞ" to EmoticonCategory.SWEAT,
    "(；´Д｀)" to EmoticonCategory.SWEAT,
    "(^o^;)" to EmoticonCategory.SWEAT,
    "(；・∀・)" to EmoticonCategory.SWEAT,
    "(；´∀｀)" to EmoticonCategory.SWEAT,
    "(-.-;)" to EmoticonCategory.DISPLEASURE,
    "(*_*;" to EmoticonCategory.SWEAT,
    "(・_・;)" to EmoticonCategory.SWEAT,
    "(ーー;)" to EmoticonCategory.SWEAT,
    "(＠_＠;)" to EmoticonCategory.SWEAT,
    "(； ･`д･´)" to EmoticonCategory.SWEAT,
    "（；^ω^）" to EmoticonCategory.SWEAT,
    "(；一_一)" to EmoticonCategory.DISPLEASURE,
    "(゜o゜;" to EmoticonCategory.SWEAT,
    "(・。・;" to EmoticonCategory.SWEAT,
    "(_ _;)" to EmoticonCategory.SADNESS,
    "(^.^;" to EmoticonCategory.SWEAT,
    "m(_ _;)m" to EmoticonCategory.SADNESS,
    "(^O^;)" to EmoticonCategory.SWEAT,
    "Σ(´∀｀；)" to EmoticonCategory.SWEAT,
    "(~O~;)" to EmoticonCategory.DISPLEASURE,
    "(・.・;)" to EmoticonCategory.SWEAT,
    "(=o=;)" to EmoticonCategory.DISPLEASURE,
    "(@@;)" to EmoticonCategory.SWEAT,
    "(✽ ﾟдﾟ ✽)" to EmoticonCategory.SURPRISE,
    "Σ(ﾟДﾟ)" to EmoticonCategory.SURPRISE,
    "( ﾟдﾟ)" to EmoticonCategory.SURPRISE,
    "＼(◎o◎)／" to EmoticonCategory.SURPRISE,
    "(@_@)" to EmoticonCategory.SURPRISE,
    "(*_*)" to EmoticonCategory.SURPRISE,
    "(+_+)" to EmoticonCategory.SURPRISE,
    "ε≡≡ﾍ( ´Д`)ﾉ" to EmoticonCategory.SURPRISE,
    ":-O" to EmoticonCategory.SURPRISE,
    ":-|" to EmoticonCategory.SURPRISE,
    "(・o・)" to EmoticonCategory.SURPRISE,
    "(゜o゜)" to EmoticonCategory.SURPRISE,
    "(゜-゜)" to EmoticonCategory.SURPRISE,
    "(゜゜)" to EmoticonCategory.SURPRISE,
    "(?_?)" to EmoticonCategory.SURPRISE,
    "(´°̥̥̥̥̥̥̥̥ω°̥̥̥̥̥̥̥̥｀)" to EmoticonCategory.SADNESS,
    "(｡ŏ﹏ŏ)" to EmoticonCategory.SADNESS,
    "(๑´•.̫ • `๑)" to EmoticonCategory.SADNESS,
    "(´ . .̫ . `)" to EmoticonCategory.SADNESS,
    "( ･ั﹏･ั)" to EmoticonCategory.SADNESS,
    "٩(๑´0`๑)۶" to EmoticonCategory.SADNESS,
    "(･ัω･ั)" to EmoticonCategory.SADNESS,
    "(*´﹃｀*)" to EmoticonCategory.SADNESS,
    "(T_T)" to EmoticonCategory.SADNESS,
    "´・ω・｀" to EmoticonCategory.SADNESS,
    "(;_;)" to EmoticonCategory.SADNESS,
    "m(_ _)m" to EmoticonCategory.SADNESS,
    "´・ω・`" to EmoticonCategory.SADNESS,
    "m(__)m" to EmoticonCategory.SADNESS,
    "(._.)" to EmoticonCategory.SADNESS,
    "´；ω；｀" to EmoticonCategory.SADNESS,
    "＿|￣|○" to EmoticonCategory.SADNESS,
    "(ToT)" to EmoticonCategory.SADNESS,
    "(_ _)" to EmoticonCategory.SADNESS,
    "｡ﾟ(ﾟ´Д｀ﾟ)ﾟ｡" to EmoticonCategory.SADNESS,
    "( ；∀；)" to EmoticonCategory.SADNESS,
    "(TT)" to EmoticonCategory.SADNESS,
    "(__)" to EmoticonCategory.SADNESS,
    "(/_;)" to EmoticonCategory.SADNESS,
    "(:_;)" to EmoticonCategory.SADNESS,
    "(ヽ´ω`)" to EmoticonCategory.SADNESS,
    "_(._.)_" to EmoticonCategory.SADNESS,
    "(;O;)" to EmoticonCategory.SADNESS,
    "(ToT)/~~~" to EmoticonCategory.SADNESS,
    "｡･ﾟ･(ﾉД`)･ﾟ･｡" to EmoticonCategory.SADNESS,
    "´；ω；｀ﾌﾞﾜｯ" to EmoticonCategory.SADNESS,
    "(;_;)/~~~" to EmoticonCategory.SADNESS,
    "(TдT)" to EmoticonCategory.SADNESS,
    "´Д⊂ヽ" to EmoticonCategory.SADNESS,
    "(TOT)" to EmoticonCategory.SADNESS,
    "(;_:) " to EmoticonCategory.SADNESS,
    "´；ω；｀ｳｯ…" to EmoticonCategory.SADNESS,
    "_(_^_)_" to EmoticonCategory.SADNESS,
    "｡･ﾟ･(ﾉ∀`)･ﾟ･｡" to EmoticonCategory.SADNESS,
    "( ´Д｀)=3" to EmoticonCategory.DISPLEASURE,
    "(つд⊂)ｴｰﾝ" to EmoticonCategory.SADNESS,
    "(ﾉД`)ｼｸｼｸ" to EmoticonCategory.SADNESS,
    "(T_T)/~~~" to EmoticonCategory.SADNESS,
    "´д⊂)‥ﾊｩ" to EmoticonCategory.SADNESS,
    "٩(๑`^´๑)۶" to EmoticonCategory.DISPLEASURE,
    "٩(๑òωó๑)۶" to EmoticonCategory.DISPLEASURE,
    "(-_-)" to EmoticonCategory.DISPLEASURE,
    "(-.-)" to EmoticonCategory.DISPLEASURE,
    "(=_=)" to EmoticonCategory.DISPLEASURE,
    "(~o~)" to EmoticonCategory.DISPLEASURE,
    "((+_+))" to EmoticonCategory.DISPLEASURE,
    "(~_~)" to EmoticonCategory.DISPLEASURE,
    "(--)" to EmoticonCategory.DISPLEASURE,
    "(・へ・)" to EmoticonCategory.DISPLEASURE,
    "(ー_ー;)" to EmoticonCategory.DISPLEASURE,
    "(-_-メ)" to EmoticonCategory.DISPLEASURE,
    "(# ﾟДﾟ)" to EmoticonCategory.DISPLEASURE,
    "(=_=;)" to EmoticonCategory.DISPLEASURE,
    "(ー_ー)" to EmoticonCategory.DISPLEASURE,
    "(ーー゛)" to EmoticonCategory.DISPLEASURE,
    "( 一一)" to EmoticonCategory.DISPLEASURE,
    "(｀´）" to EmoticonCategory.DISPLEASURE,
    "(－－〆)" to EmoticonCategory.DISPLEASURE,
    "(#･∀･)" to EmoticonCategory.DISPLEASURE,
    "(~_~メ)" to EmoticonCategory.DISPLEASURE,
    "(^_^メ)" to EmoticonCategory.DISPLEASURE,
    "(~O~)" to EmoticonCategory.DISPLEASURE,
    ":(" to EmoticonCategory.DISPLEASURE,
    ":-(" to EmoticonCategory.DISPLEASURE,
    ":|" to EmoticonCategory.DISPLEASURE,
    ":-P" to EmoticonCategory.DISPLEASURE,
)

private val symbolCategoryMap = mapOf(
    "☆" to SymbolCategory.GENERAL,
    "★" to SymbolCategory.GENERAL,
    "‥" to SymbolCategory.GENERAL,
    "…" to SymbolCategory.GENERAL,
    "♪" to SymbolCategory.GENERAL,
    "♬" to SymbolCategory.GENERAL,
    "♭" to SymbolCategory.GENERAL,
    "♯" to SymbolCategory.GENERAL,
    "♤" to SymbolCategory.GENERAL,
    "♡" to SymbolCategory.GENERAL,
    "♢" to SymbolCategory.GENERAL,
    "♧" to SymbolCategory.GENERAL,
    "♠" to SymbolCategory.GENERAL,
    "♥" to SymbolCategory.GENERAL,
    "♦" to SymbolCategory.GENERAL,
    "♣" to SymbolCategory.GENERAL,
    "〒" to SymbolCategory.GENERAL,
    "℡" to SymbolCategory.GENERAL,
    "☎" to SymbolCategory.GENERAL,
    "☏" to SymbolCategory.GENERAL,
    "■" to SymbolCategory.GENERAL,
    "□" to SymbolCategory.GENERAL,
    "▲" to SymbolCategory.GENERAL,
    "△" to SymbolCategory.GENERAL,
    "▼" to SymbolCategory.GENERAL,
    "▽" to SymbolCategory.GENERAL,
    "◆" to SymbolCategory.GENERAL,
    "◇" to SymbolCategory.GENERAL,
    "○" to SymbolCategory.GENERAL,
    "◎" to SymbolCategory.GENERAL,
    "●" to SymbolCategory.GENERAL,
    "※" to SymbolCategory.GENERAL,
    "✓" to SymbolCategory.GENERAL,
    "✔" to SymbolCategory.GENERAL,
    "✗" to SymbolCategory.GENERAL,
    "✘" to SymbolCategory.GENERAL,
    "♔" to SymbolCategory.GENERAL,
    "♕" to SymbolCategory.GENERAL,
    "♖" to SymbolCategory.GENERAL,
    "♗" to SymbolCategory.GENERAL,
    "♘" to SymbolCategory.GENERAL,
    "♙" to SymbolCategory.GENERAL,
    "♚" to SymbolCategory.GENERAL,
    "♛" to SymbolCategory.GENERAL,
    "♜" to SymbolCategory.GENERAL,
    "♝" to SymbolCategory.GENERAL,
    "♞" to SymbolCategory.GENERAL,
    "♟" to SymbolCategory.GENERAL,
    "⚀" to SymbolCategory.GENERAL,
    "⚁" to SymbolCategory.GENERAL,
    "⚂" to SymbolCategory.GENERAL,
    "⚃" to SymbolCategory.GENERAL,
    "⚄" to SymbolCategory.GENERAL,
    "⚅" to SymbolCategory.GENERAL,
    "♀" to SymbolCategory.GENERAL,
    "♂" to SymbolCategory.GENERAL,
    "仝" to SymbolCategory.GENERAL,
    "彡" to SymbolCategory.GENERAL,
    "〃" to SymbolCategory.GENERAL,
    "々" to SymbolCategory.GENERAL,
    "〆" to SymbolCategory.GENERAL,
    "〓" to SymbolCategory.GENERAL,
    "゛" to SymbolCategory.GENERAL,
    "゜" to SymbolCategory.GENERAL,
    "ゝ" to SymbolCategory.GENERAL,
    "ゞ" to SymbolCategory.GENERAL,
    "・" to SymbolCategory.GENERAL,
    "ー" to SymbolCategory.GENERAL,
    "ヽ" to SymbolCategory.GENERAL,
    "ヾ" to SymbolCategory.GENERAL,
    "." to SymbolCategory.HALF,
    "," to SymbolCategory.HALF,
    "!" to SymbolCategory.HALF,
    "?" to SymbolCategory.HALF,
    "'" to SymbolCategory.HALF,
    "\"" to SymbolCategory.HALF,
    ":" to SymbolCategory.HALF,
    ";" to SymbolCategory.HALF,
    "/" to SymbolCategory.HALF,
    "\\" to SymbolCategory.HALF,
    "~" to SymbolCategory.HALF,
    "_" to SymbolCategory.HALF,
    "+" to SymbolCategory.HALF,
    "-" to SymbolCategory.HALF,
    "*" to SymbolCategory.HALF,
    "=" to SymbolCategory.HALF,
    "@" to SymbolCategory.HALF,
    "#" to SymbolCategory.HALF,
    "%" to SymbolCategory.MATH,
    "&" to SymbolCategory.HALF,
    "<" to SymbolCategory.PARENTHESIS,
    ">" to SymbolCategory.PARENTHESIS,
    "^" to SymbolCategory.HALF,
    "|" to SymbolCategory.HALF,
    "$" to SymbolCategory.HALF,
    " " to SymbolCategory.HALF,
    "．" to SymbolCategory.HALF,
    "，" to SymbolCategory.HALF,
    "！" to SymbolCategory.HALF,
    "？" to SymbolCategory.HALF,
    "‘" to SymbolCategory.PARENTHESIS,
    "’" to SymbolCategory.PARENTHESIS,
    "：" to SymbolCategory.HALF,
    "；" to SymbolCategory.HALF,
    "／" to SymbolCategory.HALF,
    "＼" to SymbolCategory.HALF,
    "―" to SymbolCategory.HALF,
    "＿" to SymbolCategory.HALF,
    "＋" to SymbolCategory.MATH,
    "−" to SymbolCategory.MATH,
    "＊" to SymbolCategory.HALF,
    "＝" to SymbolCategory.MATH,
    "＠" to SymbolCategory.HALF,
    "＃" to SymbolCategory.HALF,
    "％" to SymbolCategory.HALF,
    "＆" to SymbolCategory.HALF,
    "（" to SymbolCategory.PARENTHESIS,
    "）" to SymbolCategory.PARENTHESIS,
    "＜" to SymbolCategory.MATH,
    "＞" to SymbolCategory.MATH,
    "＾" to SymbolCategory.HALF,
    "｜" to SymbolCategory.HALF,
    "＄" to SymbolCategory.MATH,
    "￥" to SymbolCategory.MATH,
    "　" to SymbolCategory.HALF,
    "(" to SymbolCategory.PARENTHESIS,
    ")" to SymbolCategory.PARENTHESIS,
    "[" to SymbolCategory.PARENTHESIS,
    "]" to SymbolCategory.PARENTHESIS,
    "{" to SymbolCategory.PARENTHESIS,
    "}" to SymbolCategory.PARENTHESIS,
    "「" to SymbolCategory.PARENTHESIS,
    "」" to SymbolCategory.PARENTHESIS,
    "【" to SymbolCategory.PARENTHESIS,
    "】" to SymbolCategory.PARENTHESIS,
    "『" to SymbolCategory.PARENTHESIS,
    "』" to SymbolCategory.PARENTHESIS,
    "［" to SymbolCategory.PARENTHESIS,
    "］" to SymbolCategory.PARENTHESIS,
    "〈" to SymbolCategory.PARENTHESIS,
    "〉" to SymbolCategory.PARENTHESIS,
    "《" to SymbolCategory.PARENTHESIS,
    "》" to SymbolCategory.PARENTHESIS,
    "“" to SymbolCategory.PARENTHESIS,
    "”" to SymbolCategory.PARENTHESIS,
    "〔" to SymbolCategory.PARENTHESIS,
    "〕" to SymbolCategory.PARENTHESIS,
    "｛" to SymbolCategory.PARENTHESIS,
    "｝" to SymbolCategory.PARENTHESIS,
    "〝" to SymbolCategory.PARENTHESIS,
    "〟" to SymbolCategory.PARENTHESIS,
    "┌" to SymbolCategory.PARENTHESIS,
    "├" to SymbolCategory.PARENTHESIS,
    "└" to SymbolCategory.PARENTHESIS,
    "┝" to SymbolCategory.PARENTHESIS,
    "┐" to SymbolCategory.PARENTHESIS,
    "┤" to SymbolCategory.PARENTHESIS,
    "┘" to SymbolCategory.PARENTHESIS,
    "┥" to SymbolCategory.PARENTHESIS,
    "┬" to SymbolCategory.PARENTHESIS,
    "┼" to SymbolCategory.PARENTHESIS,
    "┴" to SymbolCategory.PARENTHESIS,
    "┰" to SymbolCategory.PARENTHESIS,
    "━" to SymbolCategory.PARENTHESIS,
    "│" to SymbolCategory.PARENTHESIS,
    "┃" to SymbolCategory.PARENTHESIS,
    "┸" to SymbolCategory.PARENTHESIS,
    "┏" to SymbolCategory.PARENTHESIS,
    "┣" to SymbolCategory.PARENTHESIS,
    "┗" to SymbolCategory.PARENTHESIS,
    "┠" to SymbolCategory.PARENTHESIS,
    "┓" to SymbolCategory.PARENTHESIS,
    "┫" to SymbolCategory.PARENTHESIS,
    "┛" to SymbolCategory.PARENTHESIS,
    "┨" to SymbolCategory.PARENTHESIS,
    "┳" to SymbolCategory.PARENTHESIS,
    "╋" to SymbolCategory.PARENTHESIS,
    "┻" to SymbolCategory.PARENTHESIS,
    "╂" to SymbolCategory.PARENTHESIS,
    "┯" to SymbolCategory.PARENTHESIS,
    "┿" to SymbolCategory.PARENTHESIS,
    "┷" to SymbolCategory.PARENTHESIS,
    "→" to SymbolCategory.ARROW,
    "←" to SymbolCategory.ARROW,
    "↑" to SymbolCategory.ARROW,
    "↓" to SymbolCategory.ARROW,
    "⇒" to SymbolCategory.ARROW,
    "⇐" to SymbolCategory.ARROW,
    "⇑" to SymbolCategory.ARROW,
    "⇓" to SymbolCategory.ARROW,
    "⇨" to SymbolCategory.ARROW,
    "⇦" to SymbolCategory.ARROW,
    "⇧" to SymbolCategory.ARROW,
    "⇩" to SymbolCategory.ARROW,
    "⇢" to SymbolCategory.ARROW,
    "⇠" to SymbolCategory.ARROW,
    "⇡" to SymbolCategory.ARROW,
    "⇣" to SymbolCategory.ARROW,
    "↗" to SymbolCategory.ARROW,
    "↘" to SymbolCategory.ARROW,
    "↖" to SymbolCategory.ARROW,
    "↙" to SymbolCategory.ARROW,
    "⇗" to SymbolCategory.ARROW,
    "⇘" to SymbolCategory.ARROW,
    "⇖" to SymbolCategory.ARROW,
    "⇙" to SymbolCategory.ARROW,
    "⇛" to SymbolCategory.ARROW,
    "⇚" to SymbolCategory.ARROW,
    "☞" to SymbolCategory.ARROW,
    "☜" to SymbolCategory.ARROW,
    "↕" to SymbolCategory.ARROW,
    "↔" to SymbolCategory.ARROW,
    "⇕" to SymbolCategory.ARROW,
    "⇔" to SymbolCategory.ARROW,
    "↷" to SymbolCategory.ARROW,
    "↶" to SymbolCategory.ARROW,
    "↺" to SymbolCategory.ARROW,
    "↻" to SymbolCategory.ARROW,
    "↱" to SymbolCategory.ARROW,
    "↰" to SymbolCategory.ARROW,
    "↳" to SymbolCategory.ARROW,
    "↲" to SymbolCategory.ARROW,
    "↴" to SymbolCategory.ARROW,
    "↵" to SymbolCategory.ARROW,
    "↪" to SymbolCategory.ARROW,
    "↩" to SymbolCategory.ARROW,
    "⇇" to SymbolCategory.ARROW,
    "⇉" to SymbolCategory.ARROW,
    "⇈" to SymbolCategory.ARROW,
    "⇊" to SymbolCategory.ARROW,
    "⇄" to SymbolCategory.ARROW,
    "⇆" to SymbolCategory.ARROW,
    "⇅" to SymbolCategory.ARROW,
    "⤵" to SymbolCategory.ARROW,
    "Ⅰ" to SymbolCategory.ARROW,
    "Ⅱ" to SymbolCategory.ARROW,
    "Ⅲ" to SymbolCategory.ARROW,
    "Ⅳ" to SymbolCategory.ARROW,
    "Ⅴ" to SymbolCategory.ARROW,
    "Ⅵ" to SymbolCategory.ARROW,
    "Ⅶ" to SymbolCategory.ARROW,
    "Ⅷ" to SymbolCategory.ARROW,
    "Ⅸ" to SymbolCategory.ARROW,
    "Ⅹ" to SymbolCategory.ARROW,
    "ⅰ" to SymbolCategory.ARROW,
    "ⅱ" to SymbolCategory.ARROW,
    "ⅲ" to SymbolCategory.ARROW,
    "ⅳ" to SymbolCategory.ARROW,
    "ⅴ" to SymbolCategory.ARROW,
    "ⅵ" to SymbolCategory.ARROW,
    "ⅶ" to SymbolCategory.ARROW,
    "ⅷ" to SymbolCategory.ARROW,
    "ⅸ" to SymbolCategory.ARROW,
    "ⅹ" to SymbolCategory.ARROW,
    "㈠" to SymbolCategory.ARROW,
    "㈡" to SymbolCategory.ARROW,
    "㈢" to SymbolCategory.ARROW,
    "㈣" to SymbolCategory.ARROW,
    "㈤" to SymbolCategory.ARROW,
    "㈥" to SymbolCategory.ARROW,
    "㈦" to SymbolCategory.ARROW,
    "㈧" to SymbolCategory.ARROW,
    "㈨" to SymbolCategory.ARROW,
    "㈩" to SymbolCategory.ARROW,
    "①" to SymbolCategory.ARROW,
    "②" to SymbolCategory.ARROW,
    "③" to SymbolCategory.ARROW,
    "④" to SymbolCategory.ARROW,
    "⑤" to SymbolCategory.ARROW,
    "⑥" to SymbolCategory.ARROW,
    "⑦" to SymbolCategory.ARROW,
    "⑧" to SymbolCategory.ARROW,
    "⑨" to SymbolCategory.ARROW,
    "⑩" to SymbolCategory.ARROW,
    "⑪" to SymbolCategory.ARROW,
    "⑫" to SymbolCategory.ARROW,
    "⑬" to SymbolCategory.ARROW,
    "⑭" to SymbolCategory.ARROW,
    "⑮" to SymbolCategory.ARROW,
    "⑯" to SymbolCategory.ARROW,
    "⑰" to SymbolCategory.ARROW,
    "⑱" to SymbolCategory.ARROW,
    "⑲" to SymbolCategory.ARROW,
    "⑳" to SymbolCategory.ARROW,
    "×" to SymbolCategory.MATH,
    "÷" to SymbolCategory.MATH,
    "±" to SymbolCategory.MATH,
    "≒" to SymbolCategory.MATH,
    "≠" to SymbolCategory.MATH,
    "≦" to SymbolCategory.MATH,
    "≧" to SymbolCategory.MATH,
    "≪" to SymbolCategory.MATH,
    "≫" to SymbolCategory.MATH,
    "⊕" to SymbolCategory.MATH,
    "∅" to SymbolCategory.MATH,
    "⊂" to SymbolCategory.MATH,
    "⊃" to SymbolCategory.MATH,
    "⊆" to SymbolCategory.MATH,
    "⊇" to SymbolCategory.MATH,
    "∩" to SymbolCategory.MATH,
    "∪" to SymbolCategory.MATH,
    "∧" to SymbolCategory.MATH,
    "∨" to SymbolCategory.MATH,
    "∈" to SymbolCategory.MATH,
    "∉" to SymbolCategory.MATH,
    "∋" to SymbolCategory.MATH,
    "∌" to SymbolCategory.MATH,
    "∑" to SymbolCategory.MATH,
    "√" to SymbolCategory.MATH,
    "∀" to SymbolCategory.MATH,
    "∂" to SymbolCategory.MATH,
    "∃" to SymbolCategory.MATH,
    "∇" to SymbolCategory.MATH,
    "⌒" to SymbolCategory.MATH,
    "¬" to SymbolCategory.MATH,
    "¶" to SymbolCategory.MATH,
    "⊥" to SymbolCategory.MATH,
    "⊿" to SymbolCategory.MATH,
    "‖" to SymbolCategory.MATH,
    "∝" to SymbolCategory.MATH,
    "∞" to SymbolCategory.MATH,
    "∟" to SymbolCategory.MATH,
    "∠" to SymbolCategory.MATH,
    "∽" to SymbolCategory.MATH,
    "≡" to SymbolCategory.MATH,
    "∴" to SymbolCategory.MATH,
    "∵" to SymbolCategory.MATH,
    "¹" to SymbolCategory.MATH,
    "²" to SymbolCategory.MATH,
    "³" to SymbolCategory.MATH,
    "⁴" to SymbolCategory.MATH,
    "₁" to SymbolCategory.MATH,
    "₂" to SymbolCategory.MATH,
    "₃" to SymbolCategory.MATH,
    "₄" to SymbolCategory.MATH,
    "ⁿ" to SymbolCategory.MATH,
    "∫" to SymbolCategory.MATH,
    "∬" to SymbolCategory.MATH,
    "∮" to SymbolCategory.MATH,
    "₰" to SymbolCategory.MATH,
    "₱" to SymbolCategory.MATH,
    "₲" to SymbolCategory.MATH,
    "₳" to SymbolCategory.MATH,
    "₴" to SymbolCategory.MATH,
    "₵" to SymbolCategory.MATH,
    "№" to SymbolCategory.MATH,
    "§" to SymbolCategory.MATH,
    "㍉" to SymbolCategory.MATH,
    "㌢" to SymbolCategory.MATH,
    "㍍" to SymbolCategory.MATH,
    "㌔" to SymbolCategory.MATH,
    "㎜" to SymbolCategory.MATH,
    "㎝" to SymbolCategory.MATH,
    "m" to SymbolCategory.MATH,
    "㎞" to SymbolCategory.MATH,
    "㌘" to SymbolCategory.MATH,
    "㌧" to SymbolCategory.MATH,
    "㌍" to SymbolCategory.MATH,
    "㎎" to SymbolCategory.MATH,
    "㎏" to SymbolCategory.MATH,
    "㌫" to SymbolCategory.MATH,
    "㍑" to SymbolCategory.MATH,
    "㎡" to SymbolCategory.MATH,
    "㎥" to SymbolCategory.MATH,
    "㌶" to SymbolCategory.MATH,
    "㌻" to SymbolCategory.MATH,
    "℃" to SymbolCategory.MATH,
    "㍊" to SymbolCategory.MATH,
    "㌃" to SymbolCategory.MATH,
    "㍗" to SymbolCategory.MATH,
    "㍾" to SymbolCategory.MATH,
    "㍽" to SymbolCategory.MATH,
    "㍼" to SymbolCategory.MATH,
    "㍻" to SymbolCategory.MATH,
    "㋿" to SymbolCategory.MATH,
    "㌣" to SymbolCategory.MATH,
    "㌦" to SymbolCategory.MATH,
    "£" to SymbolCategory.MATH,
    "¢" to SymbolCategory.MATH,
    "€" to SymbolCategory.MATH,
    "₠" to SymbolCategory.MATH,
    "₡" to SymbolCategory.MATH,
    "₢" to SymbolCategory.MATH,
    "₣" to SymbolCategory.MATH,
    "₤" to SymbolCategory.MATH,
    "₥" to SymbolCategory.MATH,
    "₦" to SymbolCategory.MATH,
    "₧" to SymbolCategory.MATH,
    "₨" to SymbolCategory.MATH,
    "₩" to SymbolCategory.MATH,
    "₪" to SymbolCategory.MATH,
    "₫" to SymbolCategory.MATH,
    "₭" to SymbolCategory.MATH,
    "₮" to SymbolCategory.MATH,
    "₯" to SymbolCategory.MATH,
    "㏄" to SymbolCategory.MATH,
    "㏍" to SymbolCategory.MATH,
    "°" to SymbolCategory.MATH,
    "‰" to SymbolCategory.MATH,
    "′" to SymbolCategory.MATH,
    "″" to SymbolCategory.MATH,
    "Å" to SymbolCategory.MATH
)


/**
 * Categorizes the receiver String as an Emoticon.
 * @return The corresponding [EmoticonCategory], or [EmoticonCategory.UNKNOWN] if not found.
 */
fun String.toEmoticonCategory(): EmoticonCategory {
    return emoticonCategoryMap[this] ?: EmoticonCategory.UNKNOWN
}

/**
 * Categorizes the receiver String as a Symbol.
 * @return The corresponding [SymbolCategory], or [SymbolCategory.GENERAL] if not found.
 */
fun String.toSymbolCategory(): SymbolCategory {
    return symbolCategoryMap[this] ?: SymbolCategory.GENERAL
}
