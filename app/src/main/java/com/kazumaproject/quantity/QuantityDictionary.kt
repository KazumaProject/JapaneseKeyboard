package com.kazumaproject.quantity

import java.io.*
import java.util.zip.CRC32

/** Versioned, scoreless quantity rules. The host verifies quantity spans against its reading parser. */
class QuantityDictionary(val rules: List<List<Feature>>, val suffixes: List<Suffix>, val numericContextIds: Set<Int> = emptySet(), val counterContextIds: Set<Int> = emptySet()) {
    private val numericIds = numericContextIds.sorted().toIntArray()
    private val counterIds = counterContextIds.sorted().toIntArray()
    fun isNumericContext(id: Int): Boolean = numericIds.binarySearch(id) >= 0
    fun isCounterContext(id: Int): Boolean = counterIds.binarySearch(id) >= 0

    sealed interface Feature {
        data class Word(val text: String) : Feature
        data class Quantity(val unit: String) : Feature
    }
    data class Suffix(val base: String, val reading: String, val output: String)
    data class Token(val text: String, val reading: String, val protected: Boolean = false)
    val units: Set<String> = rules.flatten().filterIsInstance<Feature.Quantity>().map { it.unit }.toSet()
    private class RuleNode(val depth: Int) {
        val words = HashMap<String, RuleNode>()
        val quantities = HashMap<String, RuleNode>()
        val terminals = ArrayList<List<Feature>>()
    }
    private val root = RuleNode(0).also { root ->
        for (rule in rules) {
            var node = root
            for (feature in rule) {
                val children = when (feature) {
                    is Feature.Word -> node.words
                    is Feature.Quantity -> node.quantities
                }
                val key = when (feature) {
                    is Feature.Word -> feature.text
                    is Feature.Quantity -> feature.unit
                }
                node = children.getOrPut(key) { RuleNode(node.depth + 1) }
            }
            node.terminals.add(rule)
        }
    }
    private data class Span(val end: Int, val reading: String, val text: String) {
        val verified = HashMap<String, Boolean>()
    }

    /** Each quantity consumes a contiguous, verified span; word conditions retain dictionary boundaries. */
    fun matchStrength(tokens: List<Token>, ruleFilter: (List<Feature>) -> Boolean = { true }, quantityEnds: ((Int) -> List<Int>)? = null, verify: (Int, Int, String, String, String) -> Boolean): Int {
        if (rules.isEmpty()) return 0
        // Materialize each possible span once per candidate path, not once per rule.
        val spans = arrayOfNulls<List<Span>>(tokens.size)
        fun spansAt(at: Int): List<Span> {
            spans[at]?.let { return it }
            val allowed = quantityEnds?.invoke(at)?.toHashSet()
            val last = allowed?.maxOrNull() ?: if (allowed == null) tokens.lastIndex else -1
            val result = ArrayList<Span>()
            val reading = StringBuilder()
            val text = StringBuilder()
            for (end in at..last) {
                val token = tokens[end]
                if (token.protected) break
                reading.append(token.reading)
                if (reading.length > 255) break
                text.append(token.text)
                if (allowed == null || end in allowed) result.add(Span(end, reading.toString(), text.toString()))
            }
            spans[at] = result
            return result
        }
        return matchVerified(tokens.map { it.text }, ruleFilter = ruleFilter) { at, unit, _ ->
            spansAt(at).filter { span ->
                span.verified.getOrPut(unit) { verify(at, span.end, span.reading, span.text, unit) }
            }.map { it.end }.toIntArray()
        }
    }

    /** The host may supply path-verified spans without rebuilding their text for each rule. */
    fun matchVerified(words: List<String>, ruleFilter: (List<Feature>) -> Boolean = { true },
                      quantityEnds: (Int, String, Boolean) -> IntArray): Int {
        var strength = 0
        val visited = HashMap<RuleNode, MutableSet<Int>>()
        fun match(node: RuleNode, at: Int, contextual: Boolean) {
            if (!visited.getOrPut(node) { HashSet() }.add(at)) return
            if (node.depth > strength && node.terminals.any(ruleFilter)) strength = node.depth
            if (at == words.size) return
            node.words[words[at]]?.let { match(it, at + 1, contextual || node === root) }
            for ((unit, next) in node.quantities) {
                for (end in quantityEnds(at, unit, contextual)) match(next, end + 1, contextual)
            }
        }
        for (start in words.indices) match(root, start, false)
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
