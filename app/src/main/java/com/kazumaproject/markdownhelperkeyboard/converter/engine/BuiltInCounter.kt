package com.kazumaproject.markdownhelperkeyboard.converter.engine

/** Counter-local readings; these never broaden the standalone cardinal parser.
 * References: Japan Foundation, 助数詞 / 日本語教育国別シラバス IV 基数詞.
 * https://www.kyozai.jpf.go.jp/kyozai/material/BTS00010/ja/render.do
 * https://www.jpf.go.jp/j/project/japanese/survey/area/country/syllabus/pdf/sy_honyaku_4china.pdf
 * 発 variants: https://www.tofugu.com/japanese/japanese-counters-hatsu/
 */
enum class BuiltInCounter(val storageId: String, val output: String, val reading: String, val example: String) {
    THINGS("things", "つ", "つ", "ひとつ → 1つ"),
    DAY("day", "日", "にち", "ふつか → 2日"),
    MONTH("month", "月", "がつ", "しがつ → 4月"),
    HOURS("hours", "時間", "じかん", "よじかん → 4時間"),
    YEAR("year", "年", "ねん", "さんねん → 3年"),
    AGE("age", "歳", "さい", "はたち → 20歳"),
    MONTHS("months", "か月", "かげつ", "いっかげつ → 1か月"),
    PIECES("pieces", "個", "こ", "じゅういっこ → 11個"),
    LONG_OBJECTS("long_objects", "本", "ほん", "にじゅうさんぼん → 23本"),
    ANIMALS("animals", "匹", "ひき", "ろっぴき → 6匹"),
    CUPS("cups", "杯", "はい", "いっぱい → 1杯"),
    BOOKS("books", "冊", "さつ", "いっさつ → 1冊"),
    TIMES("times", "回", "かい", "ろっかい → 6回"),
    FLOORS("floors", "階", "かい", "さんがい → 3階"),
    CASES("cases", "件", "けん", "いっけん → 1件"),
    HOUSES("houses", "軒", "けん", "さんげん → 3軒"),
    HEADS("heads", "頭", "とう", "いっとう → 1頭"),
    MACHINES("machines", "台", "だい", "さんだい → 3台"),
    SHEETS("sheets", "枚", "まい", "さんまい → 3枚"),
    CLOTHES("clothes", "着", "ちゃく", "いっちゃく → 1着"),
    PAIRS("pairs", "足", "そく", "いっそく → 1足"),
    NIGHTS("nights", "泊", "はく", "ろっぱく → 6泊"),
    SHOTS("shots", "発", "はつ", "さんぱつ → 3発"),
    POINTS("points", "点", "てん", "さんてん → 3点"),
    LETTERS("letters", "通", "つう", "いっつう → 1通");

    private data class Ending(val base: String, val surface: String, val allowOrdinary: Boolean = false)

    // Build once: candidate generation runs for every input update.
    private val endings: List<Ending> by lazy {
        buildList {
            fun change(base: String, surface: String, optional: Boolean = false) { add(Ending(base, surface, optional)) }
            fun contract(six: Boolean = false, eightOptional: Boolean = false) {
                change("いち", "いっ$reading")
                if (six) change("ろく", "ろっ$reading")
                change("はち", "はっ$reading", eightOptional)
                change("じゅう", "じゅっ$reading"); change("じゅう", "じっ$reading")
            }
            fun hSeries(voiced: String, semiVoiced: String, threeOptional: Boolean = false, optionalContractions: Boolean = false) {
                change("いち", "いっ$semiVoiced"); change("ろく", "ろっ$semiVoiced", optionalContractions)
                change("はち", "はっ$semiVoiced", true)
                change("じゅう", "じゅっ$semiVoiced"); change("じゅう", "じっ$semiVoiced")
                change("ひゃく", "ひゃっ$semiVoiced", optionalContractions)
                change("びゃく", "びゃっ$semiVoiced", optionalContractions); change("ぴゃく", "ぴゃっ$semiVoiced", optionalContractions)
                change("さん", "さん$voiced", threeOptional)
                change("せん", "せん$voiced", true); change("ぜん", "ぜん$voiced", true)
                change("まん", "まん$voiced", true)
            }
            when (this@BuiltInCounter) {
                AGE, BOOKS, HEADS, CLOTHES, POINTS, LETTERS -> contract(eightOptional = this@BuiltInCounter in setOf(HEADS, CLOTHES, POINTS, LETTERS))
                PAIRS -> {
                    contract(eightOptional = true)
                    change("さん", "さんぞく", optional = true)
                }
                MONTHS, PIECES, TIMES, FLOORS, CASES, HOUSES -> {
                    contract(six = true, eightOptional = true)
                    for ((base, altered) in listOf("ひゃく" to "ひゃっ", "びゃく" to "びゃっ", "ぴゃく" to "ぴゃっ")) change(base, altered + reading)
                    if (this@BuiltInCounter == FLOORS) change("さん", "さんがい", true)
                    if (this@BuiltInCounter == HOUSES) change("さん", "さんげん")
                }
                LONG_OBJECTS -> hSeries("ぼん", "ぽん")
                ANIMALS -> hSeries("びき", "ぴき")
                CUPS -> hSeries("ばい", "ぱい")
                NIGHTS -> {
                    hSeries("ぱく", "ぱく", true)
                    change("よん", "よんぱく", true)
                }
                SHOTS -> {
                    hSeries("ぱつ", "ぱつ", threeOptional = true, optionalContractions = true)
                    change("よん", "よんぱつ", true)
                }
                HOURS -> {
                    change("よん", "よじかん"); change("きゅう", "くじかん")
                    change("なな", "しちじかん", true)
                }
                YEAR -> change("よん", "よねん", true)
                else -> Unit
            }
        }
    }

    private val exact: Map<String, Long> by lazy {
        when (this) {
            THINGS -> listOf("ひとつ", "ふたつ", "みっつ", "よっつ", "いつつ", "むっつ", "ななつ", "やっつ", "ここのつ", "とお")
                .mapIndexed { i, s -> s to i + 1L }.toMap()
            DAY -> mapOf("いちにち" to 1L, "ついたち" to 1L, "ふつか" to 2L, "みっか" to 3L, "よっか" to 4L,
                "いつか" to 5L, "むいか" to 6L, "なのか" to 7L, "ようか" to 8L, "ここのか" to 9L,
                "とおか" to 10L, "じゅうよっか" to 14L, "はつか" to 20L, "にじゅうよっか" to 24L)
            MONTH -> mapOf("しがつ" to 4L, "しちがつ" to 7L, "くがつ" to 9L)
            AGE -> mapOf("はたち" to 20L)
            else -> emptyMap()
        }
    }

    companion object {
        // Ordinary suffixes, sound changes and exact exceptions all participate.
        // Preserve enum order when several counters share a reading (e.g. 回 / 階).
        private val byLastCharacter: Map<Char, List<BuiltInCounter>> by lazy {
            entries.flatMap { counter ->
                (listOf(counter.reading) + counter.endings.map { it.surface } + counter.exact.keys)
                    .map { it.last() }.distinct().map { it to counter }
            }.groupBy({ it.first }, { it.second })
        }

        private val initialCharacters: Set<Char> by lazy {
            entries.flatMap { counter ->
                counter.endings.map { it.surface.first() } + counter.exact.keys.map { it.first() }
            }.toSet()
        }

        internal fun canStartWith(char: Char): Boolean = char in initialCharacters

        internal fun canEndWith(char: Char): Boolean = char in byLastCharacter

        internal fun matching(input: String): List<BuiltInCounter> =
            byLastCharacter[input.lastOrNull()].orEmpty()
    }

    fun parse(input: String): List<Long> {
        exact[input]?.let { return listOf(it) }
        if (this == THINGS) return emptyList()
        if (this == DAY && input.endsWith(reading)) {
            val stem = input.dropLast(reading.length)
            val n = ValidatedNumber.parseCounterReading(stem) ?:
                (if (stem.endsWith("く")) ValidatedNumber.parseCounterReading(stem.dropLast(1) + "きゅう") else null) ?: return emptyList()
            // 日 also expresses durations, so unlike calendar 月 it is not capped at 31.
            return listOfNotNull(n.takeIf { it > 0 && it !in exact.values &&
                (it % 10 != 9L || stem.endsWith("く")) })
        }
        if (this == MONTH) {
            if (!input.endsWith(reading)) return emptyList()
            return listOfNotNull(ValidatedNumber.parseCounterReading(input.dropLast(reading.length))
                ?.takeIf { it in 1..12 && it !in exact.values })
        }
        val values = endings.mapNotNull { rule ->
            if (!input.endsWith(rule.surface)) null
            else ValidatedNumber.parseCounterReading(input.dropLast(rule.surface.length) + rule.base)
        }
        if (values.isNotEmpty()) return values.distinct()
        if (!input.endsWith(reading)) return emptyList()
        val stem = input.dropLast(reading.length)
        if (endings.any { !it.allowOrdinary && stem.endsWith(it.base) }) return emptyList()
        return listOfNotNull(ValidatedNumber.parseCounterReading(stem))
    }
}
