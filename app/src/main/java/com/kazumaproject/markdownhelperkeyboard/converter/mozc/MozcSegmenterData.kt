package com.kazumaproject.markdownhelperkeyboard.converter.mozc

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class MozcPosGroup(
    val name: String,
    val id: Short,
    val ranges: List<IntRange>,
    val pattern: String,
)

class MozcPosMatcherData(
    private val groupsByName: Map<String, MozcPosGroup>,
) {
    fun getRequiredId(name: String): Short =
        groupsByName[name]?.id ?: error("Mozc POS group '$name' is missing from pos_group.dat")

    fun isInGroup(name: String, id: Short): Boolean {
        val intId = id.toInt() and 0xFFFF
        return groupsByName[name]?.ranges?.any { intId in it } == true
    }

    fun hasGroup(name: String): Boolean = groupsByName.containsKey(name)
}

data class MozcBoundaryRuleData(
    val originalSize: Int,
    val compressedLSize: Int,
    val lTable: ShortArray,
    val rTable: ShortArray,
    val bitArray: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is MozcBoundaryRuleData &&
            originalSize == other.originalSize &&
            compressedLSize == other.compressedLSize &&
            lTable.contentEquals(other.lTable) &&
            rTable.contentEquals(other.rTable) &&
            bitArray.contentEquals(other.bitArray)

    override fun hashCode(): Int {
        var result = originalSize
        result = 31 * result + compressedLSize
        result = 31 * result + lTable.contentHashCode()
        result = 31 * result + rTable.contentHashCode()
        result = 31 * result + bitArray.contentHashCode()
        return result
    }
}

data class MozcSegmenterData(
    val prefixPenalty: IntArray,
    val suffixPenalty: IntArray,
    val boundary: MozcBoundaryRuleData,
    val posMatcher: MozcPosMatcherData,
) {
    companion object {
        const val PREFIX_PENALTY_ASSET = "mozc_segmenter/prefix_penalty.dat"
        const val SUFFIX_PENALTY_ASSET = "mozc_segmenter/suffix_penalty.dat"
        const val BOUNDARY_RULE_ASSET = "mozc_segmenter/boundary_rule.dat"
        const val POS_GROUP_ASSET = "mozc_segmenter/pos_group.dat"

        fun fromInputStreams(
            prefixPenalty: InputStream,
            suffixPenalty: InputStream,
            boundaryRule: InputStream,
            posGroup: InputStream,
        ): MozcSegmenterData =
            MozcSegmenterData(
                prefixPenalty = readUInt16Array(prefixPenalty),
                suffixPenalty = readUInt16Array(suffixPenalty),
                boundary = readBoundaryRule(boundaryRule),
                posMatcher = readPosMatcher(posGroup),
            )

        private fun readUInt16Array(input: InputStream): IntArray {
            val bytes = input.use { it.readBytes() }
            require(bytes.size % 2 == 0) { "uint16 data must have even byte size: ${bytes.size}" }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            return IntArray(bytes.size / 2) { buffer.short.toInt() and 0xFFFF }
        }

        private fun readBoundaryRule(input: InputStream): MozcBoundaryRuleData {
            val bytes = input.use { it.readBytes() }
            require(bytes.size >= 5 + 16) { "boundary_rule.dat is too short" }
            require(String(bytes, 0, 5, Charsets.US_ASCII) == "MZBD1") {
                "Invalid boundary_rule.dat magic"
            }
            val buffer = ByteBuffer.wrap(bytes, 5, bytes.size - 5).order(ByteOrder.LITTLE_ENDIAN)
            val originalSize = buffer.int
            val compressedLSize = buffer.int
            val lCount = buffer.int
            val rCount = buffer.int
            val lTable = ShortArray(lCount) { buffer.short }
            val rTable = ShortArray(rCount) { buffer.short }
            val bitArraySize = buffer.int
            require(bitArraySize >= 0 && bitArraySize <= buffer.remaining()) {
                "Invalid boundary bit array size: $bitArraySize"
            }
            val bitArray = ByteArray(bitArraySize)
            buffer.get(bitArray)
            return MozcBoundaryRuleData(
                originalSize = originalSize,
                compressedLSize = compressedLSize,
                lTable = lTable,
                rTable = rTable,
                bitArray = bitArray,
            )
        }

        private fun readPosMatcher(input: InputStream): MozcPosMatcherData {
            val groups = input.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).lineSequence()
                    .filter { it.isNotBlank() }
                    .associate { line ->
                        val parts = line.split('\t')
                        require(parts.size >= 4) { "Invalid pos group line: $line" }
                        val ranges = parts[2].split(',')
                            .filter { it.isNotBlank() }
                            .map { range ->
                                val startEnd = range.split('-')
                                require(startEnd.size == 2) { "Invalid POS range: $range" }
                                startEnd[0].toInt()..startEnd[1].toInt()
                            }
                        parts[0] to MozcPosGroup(
                            name = parts[0],
                            id = parts[1].toShort(),
                            ranges = ranges,
                            pattern = parts[3],
                        )
                    }
            }
            return MozcPosMatcherData(groups)
        }
    }
}
