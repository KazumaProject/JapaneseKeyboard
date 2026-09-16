package com.kazumaproject.quantity

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.CRC32

/** Optional weighted grammar asset, separate from the v5 scoreless compatibility dictionary. */
class QuantityScoringModel(
    val posFingerprint: String,
    val connectionFingerprint: String,
    val numericLexemes: List<Lexeme>,
    val unitLexemes: List<UnitLexeme>,
    val constructionWeights: IntArray,
    val quantityClasses: Map<String, Long>,
    val lexicalClasses: List<LexicalClass>,
    val rules: List<Rule>,
    val quantityLexemes: List<QuantityLexeme> = emptyList(),
) {
    data class Lexeme(val reading: String, val text: String, val value: Long,
                      val left: Int, val right: Int, val cost: Int)
    data class QuantityLexeme(val reading: String, val text: String, val value: Long, val unit: String,
                              val left: Int, val right: Int, val cost: Int)
    enum class UnitRole { COUNTER, SUFFIX }
    data class UnitLexeme(val reading: String, val text: String, val role: UnitRole,
                          val left: Int, val right: Int, val cost: Int)
    data class LexicalClass(val text: String, val left: Int, val right: Int, val classes: Long)
    enum class FeatureKind { QUANTITY, LEXICAL }
    data class Feature(val kind: FeatureKind, val classes: Long)
    data class Rule(val features: List<Feature>, val adjustment: Int)
    private data class LexicalKey(val text: String, val left: Int, val right: Int)
    private val numericByReading = numericLexemes.groupBy { it.reading }
    private val quantitiesByReading = quantityLexemes.groupBy { it.reading }
    fun quantities(reading: String): List<QuantityLexeme> = quantitiesByReading[reading].orEmpty()
    private val unitsByReading = unitLexemes.groupBy { it.reading }
    private val classIndex = lexicalClasses.associate { LexicalKey(it.text, it.left, it.right) to it.classes }
    fun numbers(reading: String): List<Lexeme> = numericByReading[reading].orEmpty()
    fun units(reading: String): List<UnitLexeme> = unitsByReading[reading].orEmpty()
    fun classes(text: String, left: Int, right: Int): Long = classIndex[LexicalKey(text, left, right)] ?: 0L
    val suffixJoiningCost: Int get() = constructionWeights.getOrElse(3) { 0 }
    fun constructionCost(features: CardinalGrammar.Features): Int =
        features.smallProducts * constructionWeights[0] + features.largeProducts * constructionWeights[1] +
            features.additions * constructionWeights[2]

    init {
        require(posFingerprint.matches(Regex("[0-9a-f]{64}")) && connectionFingerprint.matches(Regex("[0-9a-f]{64}")))
        require(constructionWeights.size in 3..4 && constructionWeights.all { it in -20000..20000 })
        require(numericLexemes.size in 1..10000 && unitLexemes.size <= 10000 && lexicalClasses.size <= 100000 && rules.size <= 10000)
        fun lexical(reading: String, text: String, left: Int, right: Int, cost: Int) {
            require(reading.length in 1..128 && text.length in 1..128)
            require(left in 0..32767 && right in 0..32767 && cost in -32768..32767)
        }
        numericLexemes.forEach { lexical(it.reading, it.text, it.left, it.right, it.cost); require(it.value >= 0) }
        require(quantityLexemes.size <= 10000)
        quantityLexemes.forEach { lexical(it.reading, it.text, it.left, it.right, it.cost); require(it.value >= 0 && it.unit.length in 1..64) }
        unitLexemes.forEach { lexical(it.reading, it.text, it.left, it.right, it.cost) }
        require(quantityClasses.size <= 256 && quantityClasses.all { it.key.length in 1..64 && it.value > 0 })
        require(lexicalClasses.all { it.text.length in 1..128 && it.left in 0..32767 && it.right in 0..32767 && it.classes > 0 })
        require(lexicalClasses.map { LexicalKey(it.text, it.left, it.right) }.distinct().size == lexicalClasses.size)
        // A conversion has at most 255 input positions. Bound the total correction
        // per position as well as individual rules so additive path costs stay in Int.
        require(rules.sumOf { kotlin.math.abs(it.adjustment.toLong()) } <= 4_000_000L)
        rules.forEach { rule ->
            require(rule.features.size in 2..5 && rule.adjustment in -20000..20000)
            require(rule.features.all { it.classes > 0 && java.lang.Long.bitCount(it.classes) == 1 } && rule.features.any { it.kind == FeatureKind.QUANTITY })
        }
    }

    fun write(): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeUTF(posFingerprint); out.writeUTF(connectionFingerprint)
            repeat(4) { out.writeInt(constructionWeights.getOrElse(it) { 0 }) }
            out.writeInt(numericLexemes.size)
            for (word in numericLexemes) {
                out.writeUTF(word.reading); out.writeUTF(word.text); out.writeLong(word.value)
                out.writeInt(word.left); out.writeInt(word.right); out.writeInt(word.cost)
            }
            out.writeInt(unitLexemes.size)
            for (word in unitLexemes) {
                out.writeUTF(word.reading); out.writeUTF(word.text); out.writeByte(word.role.ordinal)
                out.writeInt(word.left); out.writeInt(word.right); out.writeInt(word.cost)
            }
            out.writeInt(quantityClasses.size)
            for ((unit, classes) in quantityClasses.toSortedMap()) { out.writeUTF(unit); out.writeLong(classes) }
            out.writeInt(lexicalClasses.size)
            for (word in lexicalClasses) { out.writeUTF(word.text); out.writeInt(word.left); out.writeInt(word.right); out.writeLong(word.classes) }
            out.writeInt(rules.size)
            for (rule in rules) {
                out.writeInt(rule.adjustment); out.writeByte(rule.features.size)
                for (feature in rule.features) { out.writeByte(feature.kind.ordinal); out.writeLong(feature.classes) }
            }
        }
        DataOutputStream(bytes).use { out ->
            out.writeInt(quantityLexemes.size)
            for (word in quantityLexemes) {
                out.writeUTF(word.reading); out.writeUTF(word.text); out.writeLong(word.value); out.writeUTF(word.unit)
                out.writeInt(word.left); out.writeInt(word.right); out.writeInt(word.cost)
            }
        }
        val payload = bytes.toByteArray()
        return ByteArrayOutputStream().also { stream -> DataOutputStream(stream).use { out ->
            out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(payload.size)
            out.writeLong(CRC32().apply { update(payload) }.value); out.write(payload)
        } }.toByteArray()
    }

    companion object {
        private const val MAGIC = 0x514D4F44
        const val VERSION = 1
        fun read(bytes: ByteArray): QuantityScoringModel {
            require(bytes.size in 20..16_777_216)
            val input = DataInputStream(ByteArrayInputStream(bytes))
            require(input.readInt() == MAGIC && input.readInt() == VERSION)
            val length = input.readInt(); val checksum = input.readLong()
            require(length == input.available())
            val payload = ByteArray(length); input.readFully(payload)
            require(CRC32().apply { update(payload) }.value == checksum)
            return DataInputStream(ByteArrayInputStream(payload)).use { data ->
                fun count(maximum: Int): Int = data.readInt().also { require(it in 0..maximum) }
                val pos = data.readUTF(); val connection = data.readUTF(); val weights = IntArray(4) { data.readInt() }
                val numbers = List(count(10000)) { Lexeme(data.readUTF(), data.readUTF(), data.readLong(), data.readInt(), data.readInt(), data.readInt()) }
                val units = List(count(10000)) { UnitLexeme(data.readUTF(), data.readUTF(), UnitRole.entries[data.readUnsignedByte()], data.readInt(), data.readInt(), data.readInt()) }
                val quantityClasses = LinkedHashMap<String, Long>()
                repeat(count(256)) { val key = data.readUTF(); require(quantityClasses.put(key, data.readLong()) == null) }
                val classes = List(count(100000)) { LexicalClass(data.readUTF(), data.readInt(), data.readInt(), data.readLong()) }
                val rules = List(count(10000)) {
                    val weight = data.readInt(); val size = data.readUnsignedByte(); require(size in 2..5)
                    Rule(List(size) { Feature(FeatureKind.entries[data.readUnsignedByte()], data.readLong()) }, weight)
                }
                val quantities = List(count(10000)) { QuantityLexeme(data.readUTF(), data.readUTF(), data.readLong(), data.readUTF(),
                    data.readInt(), data.readInt(), data.readInt()) }
                require(data.available() == 0)
                QuantityScoringModel(pos, connection, numbers, units, weights, quantityClasses, classes, rules, quantities)
            }
        }
    }
}
