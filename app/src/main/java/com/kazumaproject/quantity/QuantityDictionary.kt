package com.kazumaproject.quantity

import java.io.*
import java.util.zip.CRC32

/** Versioned, scoreless quantity rules. The host verifies quantity spans against its reading parser. */
class QuantityDictionary(val rules: List<List<Feature>>, val suffixes: List<Suffix>, val numericContextIds: Set<Int> = emptySet(), val counterContextIds: Set<Int> = emptySet()) {
    sealed interface Feature {
        data class Word(val text: String) : Feature
        data class Quantity(val unit: String) : Feature
    }
    data class Suffix(val base: String, val reading: String, val output: String)
    data class Token(val text: String, val reading: String, val protected: Boolean = false)
    val units: Set<String> = rules.flatten().filterIsInstance<Feature.Quantity>().map { it.unit }.toSet()
    private val firstWords = rules.filter { it.first() is Feature.Word }
        .groupBy { (it.first() as Feature.Word).text }
    private val firstQuantities = rules.filter { it.first() is Feature.Quantity }

    /** Each quantity consumes a contiguous, verified span; word conditions retain dictionary boundaries. */
    fun matchStrength(tokens: List<Token>, ruleFilter: (List<Feature>) -> Boolean = { true }, quantityEnds: ((Int) -> List<Int>)? = null, verify: (Int, Int, String, String, String) -> Boolean): Int {
        if (rules.isEmpty()) return 0
        val memo = HashMap<Triple<Int, Int, String>, Boolean>()
        fun match(rule: List<Feature>, feature: Int, at: Int): Boolean {
            if (feature == rule.size) return true
            if (at == tokens.size) return false
            return when (val f = rule[feature]) {
                is Feature.Word -> tokens[at].text == f.text && match(rule, feature + 1, at + 1)
                is Feature.Quantity -> {
                    val allowedEnds = quantityEnds?.invoke(at)?.toSet()
                    val lastEnd = allowedEnds?.maxOrNull() ?: if (allowedEnds == null) tokens.lastIndex else return false
                    val text = StringBuilder(); val reading = StringBuilder()
                    for (end in at..lastEnd) {
                        if (tokens[end].protected) break
                        text.append(tokens[end].text); reading.append(tokens[end].reading)
                        if (reading.length > 255) break
                        if (allowedEnds != null && end !in allowedEnds) continue
                        if (memo.getOrPut(Triple(at, end, f.unit)) { verify(at, end, reading.toString(), text.toString(), f.unit) } &&
                            match(rule, feature + 1, end + 1)) return true
                    }
                    false
                }
            }
        }
        var strength = 0
        for (start in tokens.indices) for (rule in firstWords[tokens[start].text].orEmpty() + firstQuantities) {
            if (rule.size > strength && ruleFilter(rule) && match(rule, 0, start)) strength = rule.size
        }
        return strength
    }

    fun matches(tokens: List<Token>, verify: (String, String, String) -> Boolean): Boolean =
        matchStrength(tokens) { _, _, reading, text, unit -> verify(reading, text, unit) } > 0

    fun write(): ByteArray {
        val payload = ByteArrayOutputStream()
        DataOutputStream(payload).use { out ->
            out.writeInt(numericContextIds.size); numericContextIds.sorted().forEach(out::writeInt)
            out.writeInt(counterContextIds.size); counterContextIds.sorted().forEach(out::writeInt)
            out.writeInt(suffixes.size)
            suffixes.forEach { out.writeUTF(it.base); out.writeUTF(it.reading); out.writeUTF(it.output) }
            out.writeInt(rules.size)
            rules.forEach { rule ->
                out.writeInt(rule.size)
                rule.forEach { f -> when (f) {
                    is Feature.Word -> { out.writeByte(1); out.writeUTF(f.text) }
                    is Feature.Quantity -> { out.writeByte(2); out.writeUTF(f.unit) }
                } }
            }
        }
        val bytes = payload.toByteArray()
        return ByteArrayOutputStream().also { result -> DataOutputStream(result).use {
            it.writeInt(MAGIC); it.writeInt(VERSION); it.writeInt(bytes.size)
            it.writeLong(CRC32().apply { update(bytes) }.value); it.write(bytes)
        } }.toByteArray()
    }

    companion object {
        const val VERSION = 5
        private const val MAGIC = 0x4a4b5154
        val EMPTY = QuantityDictionary(emptyList(), emptyList())
        fun read(bytes: ByteArray): QuantityDictionary = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC) { "Invalid quantity dictionary magic" }
            require(input.readInt() == VERSION) { "Unsupported quantity dictionary version" }
            val size = input.readInt(); val crc = input.readLong()
            require(size >= 0 && size == input.available()) { "Invalid quantity dictionary size" }
            val payload = ByteArray(size); input.readFully(payload)
            require(CRC32().apply { update(payload) }.value == crc) { "Quantity dictionary checksum mismatch" }
            DataInputStream(ByteArrayInputStream(payload)).use { data ->
                fun count(limit: Int): Int = data.readInt().also { require(it in 0..limit) }
                fun text(): String = data.readUTF().also { require(it.isNotBlank() && it.length <= 128) }
                val numericIds = List(count(4096)) { data.readInt().also { require(it in 0..32767) } }.toSet()
                val counterIds = List(count(4096)) { data.readInt().also { require(it in 0..32767) } }.toSet()
                val suffixes = List(count(1024)) { Suffix(text(), text(), text()) }
                val rules = List(count(100000)) {
                    val n = count(5); require(n >= 2)
                    List(n) { when (data.readUnsignedByte()) {
                        1 -> Feature.Word(text())
                        2 -> Feature.Quantity(text())
                        else -> error("Invalid quantity feature")
                    } }.also { require(it.any { f -> f is Feature.Quantity }) }
                }
                require(data.available() == 0) { "Trailing quantity dictionary data" }
                require(rules.distinct().size == rules.size && suffixes.distinct().size == suffixes.size)
                QuantityDictionary(rules, suffixes, numericIds, counterIds)
            }
        }
    }
}
