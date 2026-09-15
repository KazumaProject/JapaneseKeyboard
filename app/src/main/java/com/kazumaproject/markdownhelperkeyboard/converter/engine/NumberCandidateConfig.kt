package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Storage IDs are independent of UI labels and the legacy Candidate.type byte. */
enum class NumberCandidateKind(val storageId: String) {
    TIME("time"), PEOPLE("people"), YEN("yen"), DATE("date"), COMMA("comma"),
    LARGE_UNIT("large_unit"), EXPONENT("exponent"), SUPERSCRIPT("superscript"), SUBSCRIPT("subscript"),
    CIRCLED("circled"), BLACK_CIRCLED("black_circled"), ROMAN("roman"), PARENTHESIZED("parenthesized"), PERIOD("period");

    companion object {
        fun of(proof: ValidatedNumber, text: String): NumberCandidateKind? = when {
            proof.customUnit != null -> null
            proof.clock != null || proof.counter == "時" || proof.counter == "分" -> TIME
            proof.counter == "人" -> PEOPLE
            proof.counter == "円" -> YEN
            text in proof.basicForms -> null
            text.contains('月') && text.endsWith('日') -> DATE
            text.contains(':') || text.endsWith('分') -> TIME
            text.contains(',') -> COMMA
            text.any { it in '0'..'9' } && text.any { it in "万億兆京" } -> LARGE_UNIT
            text.any { it in '0'..'9' } && text.any { it in "⁰¹²³⁴⁵⁶⁷⁸⁹" } -> EXPONENT
            text.all { it in "⁰¹²³⁴⁵⁶⁷⁸⁹" } -> SUPERSCRIPT
            text.all { it in "₀₁₂₃₄₅₆₇₈₉" } -> SUBSCRIPT
            text.length != 1 -> null
            text[0] in 'Ⅰ'..'Ⅻ' || text[0] in 'ⅰ'..'ⅻ' -> ROMAN
            text[0] in '⑴'..'⒇' -> PARENTHESIZED
            text[0] in '⒈'..'⒛' -> PERIOD
            text[0] in '❶'..'❿' || text[0] in '⓫'..'⓴' || text == "⓿" -> BLACK_CIRCLED
            text == "⓪" || text[0] in '①'..'⑳' || text[0] in '㉑'..'㉟' || text[0] in '㊱'..'㊿' -> CIRCLED
            else -> null
        }
    }
}

enum class SpecialNumberReadingMode { EXACT, COMPOSE }

data class SpecialNumberReading(
    val value: Long,
    val reading: String,
    val mode: SpecialNumberReadingMode = SpecialNumberReadingMode.EXACT,
    val baseReading: String = "",
) {
    fun isValid(): Boolean = value >= 0 && CustomNumberUnit.validReading(reading) &&
        (mode == SpecialNumberReadingMode.EXACT ||
            (value > 0 && CustomNumberUnit.validReading(baseReading) &&
                ValidatedNumber.parseReading(baseReading)?.value == value))

    companion object {
        fun suggestBase(value: Long?): String {
            if (value == null || value < 0 || value > 9_999_999_999_999_999L) return ""
            if (value == 0L) return "れい"
            val ones = listOf("", "いち", "に", "さん", "よん", "ご", "ろく", "なな", "はち", "きゅう")
            fun section(n: Int): String = buildString {
                val thousands = n / 1000
                append(when (thousands) { 0 -> ""; 1 -> "せん"; 3 -> "さんぜん"; 8 -> "はっせん"; else -> ones[thousands] + "せん" })
                val hundreds = n / 100 % 10
                append(when (hundreds) { 0 -> ""; 1 -> "ひゃく"; 3 -> "さんびゃく"; 6 -> "ろっぴゃく"; 8 -> "はっぴゃく"; else -> ones[hundreds] + "ひゃく" })
                val tens = n / 10 % 10
                if (tens > 0) append((if (tens == 1) "" else ones[tens]) + "じゅう")
                append(ones[n % 10])
            }
            var remaining = value
            return buildString {
                for ((magnitude, suffix) in listOf(1_000_000_000_000L to "ちょう", 100_000_000L to "おく", 10_000L to "まん", 1L to "")) {
                    val coefficient = (remaining / magnitude).toInt()
                    remaining %= magnitude
                    if (coefficient == 0) continue
                    var part = section(coefficient)
                    if (suffix == "ちょう") {
                        listOf("いち" to "いっ", "はち" to "はっ", "じゅう" to "じゅっ")
                            .firstOrNull { part.endsWith(it.first) }?.let { part = part.dropLast(it.first.length) + it.second }
                    }
                    append(part); append(suffix)
                }
            }
        }
    }

    fun compose(input: String): Long? {
        if (mode != SpecialNumberReadingMode.COMPOSE || !isValid() || !input.endsWith(reading)) return null
        return ValidatedNumber.parseReading(input.dropLast(reading.length) + baseReading)?.value
    }
}
data class CustomNumberUnit(
    val id: String,
    val output: String,
    val reading: String,
    val enabled: Boolean = true,
    val specialReadings: List<SpecialNumberReading> = emptyList(),
) {
    fun isValid(): Boolean = id.isNotBlank() && id.length <= 64 && validText(output, 32) &&
        validReading(reading) && specialReadings.size <= 256 &&
        specialReadings.all { special ->
            special.isValid() &&
                (!special.reading.endsWith(reading) || ValidatedNumber.parseReading(special.reading.dropLast(reading.length))
                    ?.value?.let { it == special.value } != false)
        } &&
        specialReadings.map { it.reading }.distinct().size == specialReadings.size

    companion object {
        fun validText(value: String, max: Int): Boolean = value.isNotBlank() && value == value.trim() &&
            value.length <= max && value.none { Character.isISOControl(it) }
        fun validReading(value: String): Boolean = validText(value, 255) && value.all { it in 'ぁ'..'ゖ' || it == 'ー' }
    }
}

class NumberCandidateConfig(
    disabledKinds: Set<NumberCandidateKind> = emptySet(),
    units: List<CustomNumberUnit> = emptyList(),
    disabledCounters: Set<String> = emptySet(),
) {
    // Settings snapshots must not change underneath their compiled index or cached parse.
    val disabledKinds: Set<NumberCandidateKind> = java.util.Collections.unmodifiableSet(disabledKinds.toSet())
    val units: List<CustomNumberUnit> = java.util.Collections.unmodifiableList(units.map {
        it.copy(specialReadings = java.util.Collections.unmodifiableList(it.specialReadings.toList()))
    })
    val disabledCounters: Set<String> = java.util.Collections.unmodifiableSet(disabledCounters.toSet())
    internal val compiledUnits = if (this.units.isEmpty()) CompiledNumberUnits.EMPTY else CompiledNumberUnits(this.units)

    // Graph signatures are queried on every keystroke; hash the immutable rule snapshot once.
    private val snapshotHash = 31 * (31 * this.disabledKinds.hashCode() + this.units.hashCode()) + this.disabledCounters.hashCode()

    private data class ParsedInput(val input: String, val proofs: List<ValidatedNumber>)
    private var lastParsedInput: ParsedInput? = null

    @Synchronized
    internal fun parse(input: String): List<ValidatedNumber> {
        lastParsedInput?.takeIf { it.input == input }?.let { return it.proofs }
        val proofs = java.util.Collections.unmodifiableList(ValidatedNumber.parseUncached(input, this))
        lastParsedInput = ParsedInput(input, proofs)
        return proofs
    }

    fun copy(
        disabledKinds: Set<NumberCandidateKind> = this.disabledKinds,
        units: List<CustomNumberUnit> = this.units,
        disabledCounters: Set<String> = this.disabledCounters,
    ) = NumberCandidateConfig(disabledKinds, units, disabledCounters)

    override fun equals(other: Any?): Boolean = this === other || other is NumberCandidateConfig &&
        disabledKinds == other.disabledKinds && units == other.units && disabledCounters == other.disabledCounters
    override fun hashCode(): Int = snapshotHash
    override fun toString(): String = "NumberCandidateConfig(disabledKinds=$disabledKinds, units=$units, disabledCounters=$disabledCounters)"

    fun permits(proof: ValidatedNumber, text: String): Boolean {
        proof.customUnit?.let { old -> return compiledUnits.permits(old) }
        return proof.builtInCounter?.storageId !in disabledCounters &&
            (disabledKinds.isEmpty() || NumberCandidateKind.of(proof, text) !in disabledKinds)
    }

    fun encode(): String = JsonObject().apply {
        addProperty("version", 1)
        add("disabledKinds", JsonArray().apply { disabledKinds.forEach { add(it.storageId) } })
        add("disabledCounters", JsonArray().apply { disabledCounters.forEach { add(it) } })
        add("units", JsonArray().apply {
            units.forEach { unit -> add(JsonObject().apply {
                addProperty("id", unit.id); addProperty("output", unit.output); addProperty("reading", unit.reading)
                addProperty("enabled", unit.enabled)
                add("specialReadings", JsonArray().apply { unit.specialReadings.forEach { special -> add(JsonObject().apply {
                    addProperty("value", special.value.toString()); addProperty("reading", special.reading)
                    addProperty("mode", special.mode.name); addProperty("baseReading", special.baseReading)
                }) } })
            }) }
        })
    }.toString()

    companion object {
        fun decode(json: String?): NumberCandidateConfig = runCatching {
            if (json.isNullOrBlank() || json.length > 1_000_000) return NumberCandidateConfig()
            val root = JsonParser.parseString(json).asJsonObject
            if (root.get("version").asInt != 1) return NumberCandidateConfig()
            val disabled = root.getAsJsonArray("disabledKinds").mapNotNull { value ->
                NumberCandidateKind.entries.firstOrNull { it.storageId == value.asString }
            }.toSet()
            val units = root.getAsJsonArray("units").map { entry ->
                val unit = entry.asJsonObject
                CustomNumberUnit(unit.get("id").asString, unit.get("output").asString, unit.get("reading").asString,
                    unit.get("enabled").asBoolean, unit.getAsJsonArray("specialReadings").map {
                        SpecialNumberReading(it.asJsonObject.get("value").asString.toLong(), it.asJsonObject.get("reading").asString,
                            it.asJsonObject.get("mode")?.asString?.let(SpecialNumberReadingMode::valueOf) ?: SpecialNumberReadingMode.EXACT,
                            it.asJsonObject.get("baseReading")?.asString.orEmpty())
                    })
            }
            require(units.size <= 256)
            require(units.distinctBy { it.id }.size == units.size && units.distinctBy { it.reading to it.output }.size == units.size)
            NumberCandidateConfig(disabled, units, root.getAsJsonArray("disabledCounters")?.map { it.asString }?.toSet().orEmpty())
                .also { require(it.compiledUnits.allValid) }
        }.getOrElse { NumberCandidateConfig() }
    }
}

enum class NumberCandidateOrder(val preferenceValue: String, val indices: List<Int>) {
    HALF_FULL_KANJI("half_full_kanji", listOf(0, 1, 2)),
    HALF_KANJI_FULL("half_kanji_full", listOf(0, 2, 1)),
    FULL_HALF_KANJI("full_half_kanji", listOf(1, 0, 2)),
    FULL_KANJI_HALF("full_kanji_half", listOf(1, 2, 0)),
    KANJI_HALF_FULL("kanji_half_full", listOf(2, 0, 1)),
    KANJI_FULL_HALF("kanji_full_half", listOf(2, 1, 0));

    companion object {
        fun fromPreference(value: String?): NumberCandidateOrder =
            entries.firstOrNull { it.preferenceValue == value } ?: HALF_FULL_KANJI
    }
}
