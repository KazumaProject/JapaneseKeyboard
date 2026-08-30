package com.kazumaproject.markdownhelperkeyboard.converter.utility

/** Versioned, dependency-free codec for the small unit-target preference document. */
class UnitTargetSettingsJsonCodec(
    private val registry: UnitRegistry = UnitRegistry.Default,
) {
    fun encode(settings: Map<UnitCategory, List<UnitTargetSetting>>): String = buildString {
        append("{\"version\":")
        append(CURRENT_VERSION)
        append(",\"categories\":{")
        UnitCategory.entries.forEachIndexed { categoryIndex, category ->
            if (categoryIndex > 0) append(',')
            appendJsonString(category.storageId)
            append(":[")
            settings[category].orEmpty().forEachIndexed { targetIndex, setting ->
                if (targetIndex > 0) append(',')
                append("{\"unitId\":")
                appendJsonString(setting.unitId.value)
                append(",\"precision\":")
                appendJsonString(
                    when (val precision = setting.precision) {
                        Precision.Auto -> "auto"
                        Precision.Integer -> "integer"
                        is Precision.SignificantDigits -> precision.digits.toString()
                    }
                )
                append('}')
            }
            append(']')
        }
        append("}}")
    }

    fun decodeOrDefault(
        json: String?,
        defaults: Map<UnitCategory, List<UnitTargetSetting>> =
            UtilityCandidateConfig.defaultUnitTargets(),
    ): Map<UnitCategory, List<UnitTargetSetting>> {
        if (json.isNullOrBlank() || json.length > MAX_JSON_LENGTH) return immutableCopy(defaults)
        return try {
            val root = JsonParser(json).parse() as? JsonValue.ObjectValue
                ?: return immutableCopy(defaults)
            val version = (root.values["version"] as? JsonValue.NumberValue)
                ?.text?.toIntOrNull()
            if (version != CURRENT_VERSION) return immutableCopy(defaults)
            val categories = root.values["categories"] as? JsonValue.ObjectValue
                ?: return immutableCopy(defaults)
            UnitCategory.entries.associateWith { category ->
                val storedValue = categories.values[category.storageId]
                    ?: return@associateWith defaults[category].orEmpty().toList()
                val stored = storedValue as? JsonValue.ArrayValue
                    ?: throw InvalidJsonException()
                stored.values.asSequence().mapNotNull { value ->
                    val target = value as? JsonValue.ObjectValue ?: return@mapNotNull null
                    val idText = (target.values["unitId"] as? JsonValue.StringValue)?.text
                        ?: return@mapNotNull null
                    val precisionText = when (val precision = target.values["precision"]) {
                        is JsonValue.StringValue -> precision.text
                        is JsonValue.NumberValue -> precision.text
                        else -> return@mapNotNull null
                    }
                    val id = runCatching { UnitId(idText) }.getOrNull()
                        ?: return@mapNotNull null
                    val definition = registry.findById(id) ?: return@mapNotNull null
                    if (definition.category != category) return@mapNotNull null
                    val precision = when (precisionText) {
                        "auto" -> Precision.Auto
                        "integer" -> Precision.Integer
                        else -> {
                            val digits = precisionText.toIntOrNull()
                                ?.takeIf { it in Precision.MIN_DIGITS..Precision.MAX_DIGITS }
                                ?: return@mapNotNull null
                            Precision.SignificantDigits(digits)
                        }
                    }
                    UnitTargetSetting(id, precision)
                }.distinctBy(UnitTargetSetting::unitId)
                    .take(UtilityCandidateConfig.MAX_TARGETS_PER_CATEGORY)
                    .toList()
            }
        } catch (_: RuntimeException) {
            immutableCopy(defaults)
        }
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else append(char)
            }
        }
        append('"')
    }

    private fun immutableCopy(settings: Map<UnitCategory, List<UnitTargetSetting>>) =
        UnitCategory.entries.associateWith { settings[it].orEmpty().toList() }

    private val UnitCategory.storageId: String get() = name.lowercase()

    private sealed interface JsonValue {
        data class ObjectValue(val values: Map<String, JsonValue>) : JsonValue
        data class ArrayValue(val values: List<JsonValue>) : JsonValue
        data class StringValue(val text: String) : JsonValue
        data class NumberValue(val text: String) : JsonValue
        data class BooleanValue(val value: Boolean) : JsonValue
        data object NullValue : JsonValue
    }

    private class InvalidJsonException : RuntimeException()

    private class JsonParser(private val source: String) {
        private var offset = 0
        private var depth = 0

        fun parse(): JsonValue {
            val value = parseValue()
            skipWhitespace()
            if (offset != source.length) throw InvalidJsonException()
            return value
        }

        private fun parseValue(): JsonValue {
            skipWhitespace()
            return when (source.getOrNull(offset)) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonValue.StringValue(parseString())
                't' -> parseLiteral("true", JsonValue.BooleanValue(true))
                'f' -> parseLiteral("false", JsonValue.BooleanValue(false))
                'n' -> parseLiteral("null", JsonValue.NullValue)
                '-', in '0'..'9' -> JsonValue.NumberValue(parseNumber())
                else -> throw InvalidJsonException()
            }
        }

        private fun parseObject(): JsonValue.ObjectValue = withDepth {
            expect('{')
            skipWhitespace()
            val values = linkedMapOf<String, JsonValue>()
            if (takeIf('}')) return@withDepth JsonValue.ObjectValue(values)
            while (true) {
                skipWhitespace()
                if (source.getOrNull(offset) != '"') throw InvalidJsonException()
                val key = parseString()
                skipWhitespace()
                expect(':')
                values[key] = parseValue()
                skipWhitespace()
                if (takeIf('}')) break
                expect(',')
            }
            JsonValue.ObjectValue(values)
        }

        private fun parseArray(): JsonValue.ArrayValue = withDepth {
            expect('[')
            skipWhitespace()
            val values = mutableListOf<JsonValue>()
            if (takeIf(']')) return@withDepth JsonValue.ArrayValue(values)
            while (true) {
                values += parseValue()
                skipWhitespace()
                if (takeIf(']')) break
                expect(',')
            }
            JsonValue.ArrayValue(values)
        }

        private fun parseString(): String {
            expect('"')
            return buildString {
                while (true) {
                    val char = source.getOrNull(offset++) ?: throw InvalidJsonException()
                    when (char) {
                        '"' -> return@buildString
                        '\\' -> {
                            when (val escaped = source.getOrNull(offset++) ?: throw InvalidJsonException()) {
                                '"', '\\', '/' -> append(escaped)
                                'b' -> append('\b')
                                'f' -> append('\u000c')
                                'n' -> append('\n')
                                'r' -> append('\r')
                                't' -> append('\t')
                                'u' -> {
                                    val end = offset + 4
                                    if (end > source.length) throw InvalidJsonException()
                                    append(source.substring(offset, end).toIntOrNull(16)?.toChar()
                                        ?: throw InvalidJsonException())
                                    offset = end
                                }
                                else -> throw InvalidJsonException()
                            }
                        }
                        else -> {
                            if (char < ' ') throw InvalidJsonException()
                            append(char)
                        }
                    }
                }
            }
        }

        private fun parseNumber(): String {
            val start = offset
            if (source.getOrNull(offset) == '-') offset++
            if (source.getOrNull(offset) == '0') {
                offset++
            } else {
                if (source.getOrNull(offset)?.isDigit() != true) throw InvalidJsonException()
                while (source.getOrNull(offset)?.isDigit() == true) offset++
            }
            if (source.getOrNull(offset) == '.') {
                offset++
                if (source.getOrNull(offset)?.isDigit() != true) throw InvalidJsonException()
                while (source.getOrNull(offset)?.isDigit() == true) offset++
            }
            if (source.getOrNull(offset) == 'e' || source.getOrNull(offset) == 'E') {
                offset++
                if (source.getOrNull(offset) == '+' || source.getOrNull(offset) == '-') offset++
                if (source.getOrNull(offset)?.isDigit() != true) throw InvalidJsonException()
                while (source.getOrNull(offset)?.isDigit() == true) offset++
            }
            return source.substring(start, offset)
        }

        private fun <T : JsonValue> withDepth(block: () -> T): T {
            if (++depth > MAX_JSON_DEPTH) throw InvalidJsonException()
            return try {
                block()
            } finally {
                depth--
            }
        }

        private fun <T : JsonValue> parseLiteral(text: String, value: T): T {
            if (!source.startsWith(text, offset)) throw InvalidJsonException()
            offset += text.length
            return value
        }

        private fun skipWhitespace() {
            while (source.getOrNull(offset)?.isWhitespace() == true) offset++
        }

        private fun expect(char: Char) {
            if (source.getOrNull(offset) != char) throw InvalidJsonException()
            offset++
        }

        private fun takeIf(char: Char): Boolean {
            if (source.getOrNull(offset) != char) return false
            offset++
            return true
        }
    }

    companion object {
        const val CURRENT_VERSION = 1
        private const val MAX_JSON_LENGTH = 32_768
        private const val MAX_JSON_DEPTH = 16
    }
}
