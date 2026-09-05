package com.kazumaproject.markdownhelperkeyboard.converter.engine

import java.math.BigInteger

/**
 * The built-in numeric suffix vocabulary.  This file is intentionally data-only: parsing,
 * phonology, candidate ranking, and rendering consume this catalog through its public model.
 */
internal object BundledNumericSuffixCatalog : NumericSuffixCatalog {

    private const val TIME_RIGHT_ID: Short = 2015

    private val kanaStyles = setOf(
        SuffixStyle.CANONICAL,
        SuffixStyle.HIRAGANA,
        SuffixStyle.KATAKANA,
    )

    private fun definition(
        id: String,
        type: NumericSuffixType,
        surface: String,
        readings: List<String>,
        allowedStyles: Set<SuffixStyle> = kanaStyles,
        composition: NumericCompositionRule = NumericCompositionRule.none(),
        rightId: Short? = null,
    ): NumericSuffixDefinition = NumericSuffixDefinition(
        id = id,
        type = type,
        surfaces = listOf(NumericSurface(SuffixStyle.CANONICAL, surface)),
        allowedStyles = allowedStyles,
        readingRules = readings.map { NumericReadingRule(it) },
        composition = composition,
        rightId = rightId,
    )

    private fun rule(
        reading: String,
        condition: NumericCondition = NumericCondition.Always,
    ): NumericReadingRule = NumericReadingRule(reading, condition)

    private fun whole(
        reading: String,
        value: Long,
    ): NumericReadingRule = NumericReadingRule(
        reading = reading,
        wholeExpressionValue = BigInteger.valueOf(value),
    )

    private val pSound = NumericCondition.AnyOf(
        listOf(
            NumericCondition.LastDigitIn(setOf(1, 6, 8)),
            NumericCondition.FeatureIn(setOf(NumericPhonologicalFeature.GEMINATE)),
        ),
    )

    private val definitionsData = listOf(
        // Time and date.
        definition(
            "time.hour",
            NumericSuffixType.TIME,
            "時",
            listOf("じ", "時"),
            rightId = TIME_RIGHT_ID,
        ),
        definition(
            "time.duration",
            NumericSuffixType.TIME,
            "時間",
            listOf("じかん", "時間"),
            rightId = TIME_RIGHT_ID,
        ),
        NumericSuffixDefinition(
            id = "time.minute",
            type = NumericSuffixType.TIME,
            surfaces = listOf(NumericSurface(SuffixStyle.CANONICAL, "分")),
            allowedStyles = kanaStyles,
            readingRules = listOf(
                rule("ふん"),
                rule(
                    "ぷん",
                    NumericCondition.AnyOf(
                        listOf(
                            NumericCondition.LastDigitIn(setOf(1, 3, 6, 8)),
                            NumericCondition.FeatureIn(setOf(NumericPhonologicalFeature.GEMINATE)),
                        ),
                    ),
                ),
                rule("分"),
            ),
            rightId = TIME_RIGHT_ID,
        ),
        definition(
            "time.second",
            NumericSuffixType.TIME,
            "秒",
            listOf("びょう", "秒"),
            rightId = TIME_RIGHT_ID,
        ),
        definition("date.day", NumericSuffixType.DATE, "日", listOf("にち", "日")),
        definition("date.week", NumericSuffixType.DATE, "週", listOf("しゅう", "週")),
        definition("date.week.duration", NumericSuffixType.DATE, "週間", listOf("しゅうかん", "週間")),
        definition("date.month.duration", NumericSuffixType.DATE, "か月", listOf("かげつ", "か月", "ケ月")),
        definition("date.month", NumericSuffixType.DATE, "月", listOf("がつ", "月")),
        definition("date.year", NumericSuffixType.DATE, "年", listOf("ねん", "年")),
        NumericSuffixDefinition(
            id = "modifier.half",
            type = NumericSuffixType.OTHER,
            surfaces = listOf(NumericSurface(SuffixStyle.CANONICAL, "半")),
            allowedStyles = kanaStyles,
            readingRules = listOf(rule("はん"), rule("半")),
            composition = NumericCompositionRule(
                allowedPreviousTypes = setOf(NumericSuffixType.TIME),
            ),
            rightId = TIME_RIGHT_ID,
        ),

        // Currency and measurement units.
        definition("currency.yen", NumericSuffixType.CURRENCY, "円", listOf("えん", "円")),
        definition("currency.dollar", NumericSuffixType.CURRENCY, "ドル", listOf("どる", "ドル")),
        definition("currency.euro", NumericSuffixType.CURRENCY, "ユーロ", listOf("ゆーろ", "ユーロ")),
        definition("currency.yuan", NumericSuffixType.CURRENCY, "元", listOf("げん", "元")),
        definition("currency.pound", NumericSuffixType.CURRENCY, "ポンド", listOf("ぽんど", "ポンド")),
        definition("currency.cent", NumericSuffixType.CURRENCY, "セント", listOf("せんと", "セント")),
        definition(
            "unit.percent",
            NumericSuffixType.UNIT,
            "%",
            listOf("ぱーせんと", "%"),
            setOf(SuffixStyle.CANONICAL),
        ),
        definition(
            "unit.celsius",
            NumericSuffixType.UNIT,
            "℃",
            listOf("ど", "℃"),
            setOf(SuffixStyle.CANONICAL),
        ),
        definition(
            "unit.fahrenheit",
            NumericSuffixType.UNIT,
            "℉",
            listOf("かし", "℉"),
            setOf(SuffixStyle.CANONICAL),
        ),
        definition("unit.millimeter", NumericSuffixType.UNIT, "mm", listOf("みり", "みりめーとる", "mm")),
        definition("unit.centimeter", NumericSuffixType.UNIT, "cm", listOf("せんち", "せんちめーとる", "cm")),
        definition("unit.meter", NumericSuffixType.UNIT, "m", listOf("めーとる", "えむ", "m")),
        definition("unit.kilometer", NumericSuffixType.UNIT, "km", listOf("きろ", "きろめーとる", "km")),
        definition("unit.milligram", NumericSuffixType.UNIT, "mg", listOf("みりぐらむ", "mg")),
        definition("unit.gram", NumericSuffixType.UNIT, "g", listOf("ぐらむ", "g")),
        definition("unit.kilogram", NumericSuffixType.UNIT, "kg", listOf("きろぐらむ", "kg")),
        definition("unit.milliliter", NumericSuffixType.UNIT, "ml", listOf("みりりっとる", "ml")),
        definition("unit.liter", NumericSuffixType.UNIT, "l", listOf("りっとる", "える", "l")),

        // Common counters.  Each entry is data; the parser does not branch on these IDs.
        definition("counter.person", NumericSuffixType.COUNTER, "人", listOf("にん", "人")),
        NumericSuffixDefinition(
            id = "counter.person.irregular",
            type = NumericSuffixType.COUNTER,
            surfaces = listOf(NumericSurface(SuffixStyle.CANONICAL, "人")),
            allowedStyles = kanaStyles,
            readingRules = listOf(whole("ひとり", 1), whole("ふたり", 2)),
        ),
        definition("counter.name", NumericSuffixType.COUNTER, "名", listOf("めい", "名")),
        definition("counter.item", NumericSuffixType.COUNTER, "個", listOf("こ", "個")),
        definition("counter.item.alt", NumericSuffixType.COUNTER, "箇", listOf("こ", "箇")),
        NumericSuffixDefinition(
            id = "counter.hon",
            type = NumericSuffixType.COUNTER,
            surfaces = listOf(NumericSurface(SuffixStyle.CANONICAL, "本")),
            allowedStyles = kanaStyles,
            readingRules = listOf(
                rule("ほん"),
                rule("ぼん", NumericCondition.ModuloIn(10, setOf(3))),
                rule("ぽん", pSound),
                rule("本"),
            ),
        ),
        NumericSuffixDefinition(
            id = "counter.hiki",
            type = NumericSuffixType.COUNTER,
            surfaces = listOf(NumericSurface(SuffixStyle.CANONICAL, "匹")),
            allowedStyles = kanaStyles,
            readingRules = listOf(
                rule("ひき"),
                rule("びき", NumericCondition.ModuloIn(10, setOf(3))),
                rule("ぴき", pSound),
                rule("匹"),
            ),
        ),
        NumericSuffixDefinition(
            id = "counter.hane",
            type = NumericSuffixType.COUNTER,
            surfaces = listOf(NumericSurface(SuffixStyle.CANONICAL, "羽")),
            allowedStyles = kanaStyles,
            readingRules = listOf(
                rule("わ"),
                rule("ば", NumericCondition.ModuloIn(10, setOf(3))),
                rule("ぱ", pSound),
                rule("羽"),
            ),
        ),
        definition("counter.large.animal", NumericSuffixType.COUNTER, "頭", listOf("とう", "頭")),
        NumericSuffixDefinition(
            id = "counter.cup",
            type = NumericSuffixType.COUNTER,
            surfaces = listOf(NumericSurface(SuffixStyle.CANONICAL, "杯")),
            allowedStyles = kanaStyles,
            readingRules = listOf(
                rule("はい"),
                rule("ばい", NumericCondition.ModuloIn(10, setOf(3))),
                rule("ぱい", pSound),
                rule("杯"),
            ),
        ),
        definition("counter.sheet", NumericSuffixType.COUNTER, "枚", listOf("まい", "枚")),
        definition("counter.book", NumericSuffixType.COUNTER, "冊", listOf("さつ", "冊")),
        definition("counter.machine", NumericSuffixType.COUNTER, "台", listOf("だい", "台")),
        definition("counter.generation", NumericSuffixType.COUNTER, "代", listOf("だい", "代")),
        definition("counter.building.floor", NumericSuffixType.COUNTER, "階", listOf("かい", "階")).copy(
            readingRules = listOf(
                rule("かい"),
                rule("がい", NumericCondition.ModuloIn(10, setOf(3))),
                rule("階"),
            ),
        ),
        definition("counter.times", NumericSuffixType.COUNTER, "回", listOf("かい", "回")),
        definition("counter.age", NumericSuffixType.COUNTER, "歳", listOf("さい", "歳")),
        definition("counter.age.alt", NumericSuffixType.COUNTER, "才", listOf("さい", "才")),
        definition("counter.case", NumericSuffixType.COUNTER, "件", listOf("けん", "件")),
        definition("counter.house", NumericSuffixType.COUNTER, "軒", listOf("けん", "軒")).copy(
            readingRules = listOf(
                rule("けん"),
                rule("げん", NumericCondition.ModuloIn(10, setOf(3))),
                rule("軒"),
            ),
        ),
        definition("counter.building", NumericSuffixType.COUNTER, "棟", listOf("とう", "棟")),
        definition("counter.household", NumericSuffixType.COUNTER, "戸", listOf("こ", "戸")),
        definition("counter.point", NumericSuffixType.COUNTER, "点", listOf("てん", "点")),
        definition("counter.can", NumericSuffixType.COUNTER, "缶", listOf("かん", "缶")),
        definition("counter.bottle", NumericSuffixType.COUNTER, "瓶", listOf("びん", "ビン", "瓶")),
        definition("counter.box", NumericSuffixType.COUNTER, "箱", listOf("はこ", "箱")),
        definition("counter.bag", NumericSuffixType.COUNTER, "袋", listOf("ふくろ", "袋")),
        definition("counter.grain", NumericSuffixType.COUNTER, "粒", listOf("つぶ", "粒")),
        definition("counter.bundle", NumericSuffixType.COUNTER, "束", listOf("たば", "束")),
        definition("counter.cluster", NumericSuffixType.COUNTER, "房", listOf("ふさ", "房")),
        definition("counter.wear", NumericSuffixType.COUNTER, "着", listOf("ちゃく", "着")),
        definition("counter.shoe", NumericSuffixType.COUNTER, "足", listOf("そく", "足")),
        definition("counter.face", NumericSuffixType.COUNTER, "面", listOf("めん", "面")),
        definition("counter.letter", NumericSuffixType.COUNTER, "通", listOf("つう", "通")),
        definition("counter.part", NumericSuffixType.COUNTER, "部", listOf("ぶ", "部")),
        definition("counter.volume", NumericSuffixType.COUNTER, "巻", listOf("かん", "巻")),
        definition("counter.chapter", NumericSuffixType.COUNTER, "章", listOf("しょう", "章")),
        definition("counter.mouth", NumericSuffixType.COUNTER, "口", listOf("くち", "口")),
        definition("counter.seat", NumericSuffixType.COUNTER, "席", listOf("せき", "席")),
        definition("counter.row", NumericSuffixType.COUNTER, "列", listOf("れつ", "列")),
        definition("counter.foundation", NumericSuffixType.COUNTER, "基", listOf("き", "基")),
        definition("counter.stock", NumericSuffixType.COUNTER, "株", listOf("かぶ", "株")),
        definition("counter.formula", NumericSuffixType.COUNTER, "式", listOf("しき", "式")),
        definition("counter.block", NumericSuffixType.COUNTER, "丁", listOf("ちょう", "丁")),
        definition("counter.vessel", NumericSuffixType.COUNTER, "隻", listOf("せき", "隻")),
        definition("counter.boat", NumericSuffixType.COUNTER, "艘", listOf("そう", "艘")),

        // Ordinal and scalar expressions.
        definition("ordinal.number", NumericSuffixType.ORDINAL, "番", listOf("ばん", "番")),
        definition("ordinal.code", NumericSuffixType.ORDINAL, "号", listOf("ごう", "号")),
        definition("ordinal.numbered", NumericSuffixType.ORDINAL, "番目", listOf("ばんめ", "番目")),
        definition("ordinal.rank", NumericSuffixType.ORDINAL, "位", listOf("い", "位")),
        definition("ordinal.group", NumericSuffixType.ORDINAL, "組", listOf("くみ", "組")),
        definition("ordinal.pair", NumericSuffixType.ORDINAL, "対", listOf("つい", "対")),
        definition("ordinal.step", NumericSuffixType.ORDINAL, "段", listOf("だん", "段")),
        definition("scalar.degree", NumericSuffixType.OTHER, "度", listOf("ど", "度")),
        definition("scalar.multiple", NumericSuffixType.OTHER, "倍", listOf("ばい", "倍")),
        definition("scalar.ratio", NumericSuffixType.OTHER, "割", listOf("わり", "割")),

        // Irregular calendar readings are data, not parser branches.
        NumericSuffixDefinition(
            id = "date.day.irregular",
            type = NumericSuffixType.DATE,
            surfaces = listOf(NumericSurface(SuffixStyle.CANONICAL, "日")),
            allowedStyles = kanaStyles,
            readingRules = listOf(
                whole("ついたち", 1),
                whole("ふつか", 2),
                whole("みっか", 3),
                whole("よっか", 4),
                whole("いつか", 5),
                whole("むいか", 6),
                whole("なのか", 7),
                whole("ようか", 8),
                whole("ここのか", 9),
                whole("とおか", 10),
                whole("はつか", 20),
            ),
        ),
    )

    override val definitions: List<NumericSuffixDefinition> = definitionsData

    init {
        check(validate().isEmpty()) { validate().joinToString() }
    }
}
