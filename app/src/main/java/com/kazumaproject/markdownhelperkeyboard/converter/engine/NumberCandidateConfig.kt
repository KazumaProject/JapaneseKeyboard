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
            proof.clock != null || proof.counter in setOf("時", "分") -> TIME
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

data class SpecialNumberReading(val value: Long, val reading: String)
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
            special.value >= 0 && validReading(special.reading) &&
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

data class NumberCandidateConfig(
    val disabledKinds: Set<NumberCandidateKind> = emptySet(),
    val units: List<CustomNumberUnit> = emptyList(),
) {
    fun permits(proof: ValidatedNumber, text: String): Boolean {
        proof.customUnit?.let { old -> return units.any { it.enabled && it == old } }
        return NumberCandidateKind.of(proof, text) !in disabledKinds
    }

    fun encode(): String = JsonObject().apply {
        addProperty("version", 1)
        add("disabledKinds", JsonArray().apply { disabledKinds.forEach { add(it.storageId) } })
        add("units", JsonArray().apply {
            units.forEach { unit -> add(JsonObject().apply {
                addProperty("id", unit.id); addProperty("output", unit.output); addProperty("reading", unit.reading)
                addProperty("enabled", unit.enabled)
                add("specialReadings", JsonArray().apply { unit.specialReadings.forEach { special -> add(JsonObject().apply {
                    addProperty("value", special.value.toString()); addProperty("reading", special.reading)
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
                        SpecialNumberReading(it.asJsonObject.get("value").asString.toLong(), it.asJsonObject.get("reading").asString)
                    })
            }
            require(units.size <= 256 && units.all { it.isValid() })
            require(units.distinctBy { it.id }.size == units.size && units.distinctBy { it.reading to it.output }.size == units.size)
            NumberCandidateConfig(disabled, units)
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
