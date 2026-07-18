package com.kazumaproject.markdownhelperkeyboard.converter

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object ConnectionMatrix {
    interface CostTable {
        val matrixSize: Int
        val entryCount: Int
        fun cost(rid: Int, lid: Int): Int
    }

    fun inferMatrixSize(connectionIdsSize: Int): Int? {
        if (connectionIdsSize <= 0) return null
        val matrixSize = sqrt(connectionIdsSize.toDouble()).toInt()
        return if (matrixSize.toLong() * matrixSize.toLong() == connectionIdsSize.toLong()) {
            matrixSize
        } else {
            null
        }
    }

    fun inferMatrixSize(connectionIds: ShortArray): Int? =
        inferMatrixSize(connectionIds.size)

    fun fromShortArray(
        connectionIds: ShortArray,
        matrixSize: Int = inferMatrixSize(connectionIds)
            ?: error("connectionId.dat size ${connectionIds.size} is not a valid square matrix"),
    ): CostTable = ShortArrayCostTable(connectionIds, matrixSize)

    fun fromCompactInputStream(input: InputStream): CostTable =
        CompactCostTable(input.readBytes())

    fun fromCompactInputStreams(data: InputStream, index: InputStream): CostTable =
        CompactCostTable(
            data.readBytes(),
            readCompactIndex(ByteBuffer.wrap(index.readBytes())),
        )

    private class ShortArrayCostTable(
        private val connectionIds: ShortArray,
        override val matrixSize: Int,
    ) : CostTable {
        override val entryCount: Int = connectionIds.size

        override fun cost(rid: Int, lid: Int): Int {
            val index = indexOf(rid, lid, matrixSize, entryCount)
            return connectionIds[index].toInt()
        }
    }

    /**
     * Exact, non-quantized port of Mozc's compressed Connector layout.
     *
     * Each row stores its most frequent cost as a default. Two succinct bit vectors identify
     * the exceptional cells, followed by the original UInt16 cost values. No candidate, POS or
     * cost information is discarded.
     */
    private class CompactCostTable(
        private val data: ByteArray,
        precomputed: CompactIndex? = null,
    ) : CostTable {
        override val matrixSize: Int
        override val entryCount: Int

        private val resolution: Int
        private val defaultCosts: ShortArray
        private val chunkBitsOffsets: IntArray
        private val compactBitsOffsets: IntArray
        private val valuesOffsets: IntArray
        private val chunkRankStarts: IntArray
        private val compactRankStarts: IntArray
        private val rankPrefixes: IntArray
        private val chunkBitsSize: Int

        private val cacheKeys = IntArray(CACHE_SIZE) { -1 }
        private val cacheValues = IntArray(CACHE_SIZE)

        init {
            require(data.size >= HEADER_SIZE) { "Compact connection matrix header is truncated" }
            require(readUInt16(0) == MAGIC) {
                "Invalid compact connection matrix magic: ${readUInt16(0)}"
            }
            resolution = readUInt16(2)
            require(resolution == 1) {
                "Only exact compact connection matrices are supported: resolution=$resolution"
            }
            val rSize = readUInt16(4)
            val lSize = readUInt16(6)
            require(rSize > 0 && rSize == lSize) {
                "Compact connection matrix must be a non-empty square: rSize=$rSize lSize=$lSize"
            }
            matrixSize = rSize
            entryCount = rSize * lSize
            if (precomputed != null) {
                require(precomputed.matrixSize == matrixSize) {
                    "Compact connection index matrix size mismatch"
                }
                require(precomputed.entryCount == entryCount) {
                    "Compact connection index entry count mismatch"
                }
                require(precomputed.defaultCosts.size == matrixSize) {
                    "Compact connection default-cost size mismatch"
                }
                listOf(
                    precomputed.chunkBitsOffsets,
                    precomputed.compactBitsOffsets,
                    precomputed.valuesOffsets,
                    precomputed.chunkRankStarts,
                    precomputed.compactRankStarts,
                ).forEach { values ->
                    require(values.size == matrixSize) {
                        "Compact connection row-index size mismatch"
                    }
                }
                precomputed.chunkBitsOffsets.forEach { requireAvailable(it, precomputed.chunkBitsSize) }
                precomputed.compactBitsOffsets.forEach { requireAvailable(it, 0) }
                precomputed.valuesOffsets.forEach { requireAvailable(it, 0) }
                require(
                    precomputed.chunkRankStarts.all { it in precomputed.rankPrefixes.indices } &&
                        precomputed.compactRankStarts.all { it in precomputed.rankPrefixes.indices },
                ) { "Compact connection rank index is out of range" }
                defaultCosts = precomputed.defaultCosts
                chunkBitsSize = precomputed.chunkBitsSize
                chunkBitsOffsets = precomputed.chunkBitsOffsets
                compactBitsOffsets = precomputed.compactBitsOffsets
                valuesOffsets = precomputed.valuesOffsets
                chunkRankStarts = precomputed.chunkRankStarts
                compactRankStarts = precomputed.compactRankStarts
                rankPrefixes = precomputed.rankPrefixes
            } else {
                defaultCosts = ShortArray(matrixSize)
                var cursor = HEADER_SIZE
                defaultCosts.indices.forEach { index ->
                    requireAvailable(cursor, Short.SIZE_BYTES)
                    defaultCosts[index] = readUInt16(cursor).toShort()
                    cursor += Short.SIZE_BYTES
                }
                if ((matrixSize and 1) != 0) {
                    requireAvailable(cursor, Short.SIZE_BYTES)
                    cursor += Short.SIZE_BYTES
                }

                val chunkBitCount = (matrixSize + 7) / 8
                chunkBitsSize = ((chunkBitCount + 31) / 32) * Int.SIZE_BYTES
                chunkBitsOffsets = IntArray(matrixSize)
                compactBitsOffsets = IntArray(matrixSize)
                valuesOffsets = IntArray(matrixSize)
                val compactBitsSizes = IntArray(matrixSize)
                for (rid in 0 until matrixSize) {
                    requireAvailable(cursor, 2 * Short.SIZE_BYTES)
                    val compactBitsSize = readUInt16(cursor)
                    val valuesSize = readUInt16(cursor + Short.SIZE_BYTES)
                    require(compactBitsSize % Int.SIZE_BYTES == 0) {
                        "Compact bit vector for row $rid is not 32-bit aligned: $compactBitsSize"
                    }
                    require(valuesSize % Short.SIZE_BYTES == 0) {
                        "Compact values for row $rid are not UInt16 aligned: $valuesSize"
                    }
                    cursor += 2 * Short.SIZE_BYTES
                    requireAvailable(cursor, chunkBitsSize + compactBitsSize + valuesSize)
                    chunkBitsOffsets[rid] = cursor
                    cursor += chunkBitsSize
                    compactBitsOffsets[rid] = cursor
                    compactBitsSizes[rid] = compactBitsSize
                    cursor += compactBitsSize
                    valuesOffsets[rid] = cursor
                    cursor += valuesSize
                }
                require(cursor == data.size) {
                    "Compact connection matrix has trailing bytes: parsed=$cursor size=${data.size}"
                }

                val rankBuilder = IntArrayBuilder()
                chunkRankStarts = IntArray(matrixSize)
                compactRankStarts = IntArray(matrixSize)
                for (rid in 0 until matrixSize) {
                    chunkRankStarts[rid] = rankBuilder.size
                    appendRankPrefixes(chunkBitsOffsets[rid], chunkBitsSize, rankBuilder)
                    compactRankStarts[rid] = rankBuilder.size
                    appendRankPrefixes(compactBitsOffsets[rid], compactBitsSizes[rid], rankBuilder)
                }
                rankPrefixes = rankBuilder.toIntArray()
            }
        }

        override fun cost(rid: Int, lid: Int): Int {
            indexOf(rid, lid, matrixSize, entryCount)
            val key = (rid shl 16) or lid
            val bucket = (3 * rid + lid) and CACHE_MASK
            val firstKey = cacheKeys[bucket]
            if (firstKey == key) {
                val cached = cacheValues[bucket]
                if (cacheKeys[bucket] == firstKey) return cached
            }
            val value = lookupCost(rid, lid)
            // Cost is written before its key and the read path verifies the key twice. This keeps
            // concurrent readers exact without allocating an AtomicLongArray for the hot cache.
            cacheValues[bucket] = value
            cacheKeys[bucket] = key
            return value
        }

        private fun lookupCost(rid: Int, lid: Int): Int {
            val chunkBitPosition = lid / 8
            val chunkOffset = chunkBitsOffsets[rid]
            if (!getBit(chunkOffset, chunkBitPosition)) {
                return defaultCosts[rid].toInt() and 0xFFFF
            }
            val compactBitPosition =
                rank1(
                    dataOffset = chunkOffset,
                    bitPosition = chunkBitPosition,
                    rankStart = chunkRankStarts[rid],
                ) * 8 + lid % 8
            val compactOffset = compactBitsOffsets[rid]
            if (!getBit(compactOffset, compactBitPosition)) {
                return defaultCosts[rid].toInt() and 0xFFFF
            }
            val valuePosition = rank1(
                dataOffset = compactOffset,
                bitPosition = compactBitPosition,
                rankStart = compactRankStarts[rid],
            )
            return readUInt16(valuesOffsets[rid] + valuePosition * Short.SIZE_BYTES) * resolution
        }

        private fun rank1(
            dataOffset: Int,
            bitPosition: Int,
            rankStart: Int,
        ): Int {
            val bytePosition = bitPosition / Byte.SIZE_BITS
            val rankBlock = bytePosition / RANK_BLOCK_BYTES
            var result = rankPrefixes[rankStart + rankBlock]
            val firstWord = rankBlock * RANK_BLOCK_BYTES / Int.SIZE_BYTES
            val lastFullWord = bitPosition / Int.SIZE_BITS
            for (wordIndex in firstWord until lastFullWord) {
                result += Integer.bitCount(readInt32(dataOffset + wordIndex * Int.SIZE_BYTES))
            }
            val remainingBits = bitPosition and (Int.SIZE_BITS - 1)
            if (remainingBits > 0) {
                val mask = (1L shl remainingBits) - 1L
                result += Integer.bitCount(
                    readInt32(dataOffset + lastFullWord * Int.SIZE_BYTES) and mask.toInt(),
                )
            }
            return result
        }

        private fun appendRankPrefixes(
            dataOffset: Int,
            byteSize: Int,
            destination: IntArrayBuilder,
        ) {
            var cumulative = 0
            var blockOffset = 0
            while (blockOffset < byteSize) {
                destination.add(cumulative)
                val blockEnd = minOf(blockOffset + RANK_BLOCK_BYTES, byteSize)
                var wordOffset = blockOffset
                while (wordOffset < blockEnd) {
                    cumulative += Integer.bitCount(readInt32(dataOffset + wordOffset))
                    wordOffset += Int.SIZE_BYTES
                }
                blockOffset = blockEnd
            }
        }

        private fun getBit(offset: Int, position: Int): Boolean =
            ((data[offset + position / Byte.SIZE_BITS].toInt() ushr (position and 7)) and 1) != 0

        private fun readUInt16(offset: Int): Int =
            (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8)

        private fun readInt32(offset: Int): Int =
            (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)

        private fun requireAvailable(offset: Int, byteCount: Int) {
            require(offset >= 0 && byteCount >= 0 && offset <= data.size - byteCount) {
                "Compact connection matrix is truncated at offset=$offset bytes=$byteCount size=${data.size}"
            }
        }
    }

    private data class CompactIndex(
        val matrixSize: Int,
        val entryCount: Int,
        val chunkBitsSize: Int,
        val defaultCosts: ShortArray,
        val chunkBitsOffsets: IntArray,
        val compactBitsOffsets: IntArray,
        val valuesOffsets: IntArray,
        val chunkRankStarts: IntArray,
        val compactRankStarts: IntArray,
        val rankPrefixes: IntArray,
    )

    private fun readCompactIndex(data: ByteBuffer): CompactIndex {
        val input = data.duplicate().order(ByteOrder.BIG_ENDIAN)
        fun requireRemaining(byteCount: Int) {
            require(byteCount >= 0 && input.remaining() >= byteCount) {
                "Truncated compact connection index: need=$byteCount remaining=${input.remaining()}"
            }
        }
        fun readInt(): Int {
            requireRemaining(Int.SIZE_BYTES)
            return input.int
        }
        fun readIntArray(): IntArray {
            val size = readInt()
            require(size >= 0 && size <= input.remaining() / Int.SIZE_BYTES) {
                "Invalid compact connection IntArray size: $size"
            }
            return IntArray(size) { readInt() }
        }
        fun readShortArray(): ShortArray {
            val size = readInt()
            require(size >= 0 && size <= input.remaining() / Short.SIZE_BYTES) {
                "Invalid compact connection ShortArray size: $size"
            }
            return ShortArray(size) {
                requireRemaining(Short.SIZE_BYTES)
                input.short
            }
        }

        require(readInt() == INDEX_MAGIC) { "Invalid compact connection index magic" }
        require(readInt() == INDEX_VERSION) { "Unsupported compact connection index version" }
        val result = CompactIndex(
            matrixSize = readInt(),
            entryCount = readInt(),
            chunkBitsSize = readInt(),
            defaultCosts = readShortArray(),
            chunkBitsOffsets = readIntArray(),
            compactBitsOffsets = readIntArray(),
            valuesOffsets = readIntArray(),
            chunkRankStarts = readIntArray(),
            compactRankStarts = readIntArray(),
            rankPrefixes = readIntArray(),
        )
        require(!input.hasRemaining()) {
            "Compact connection index has ${input.remaining()} trailing bytes"
        }
        return result
    }

    private class IntArrayBuilder(initialCapacity: Int = 4096) {
        private var values = IntArray(initialCapacity)
        var size: Int = 0
            private set

        fun add(value: Int) {
            if (size == values.size) {
                values = values.copyOf((values.size * 2).coerceAtLeast(1))
            }
            values[size++] = value
        }

        fun toIntArray(): IntArray = values.copyOf(size)
    }

    private fun indexOf(
        rid: Int,
        lid: Int,
        matrixSize: Int,
        entryCount: Int,
    ): Int {
        require(matrixSize > 0) { "connectionMatrixSize must be positive: $matrixSize" }
        require(rid in 0 until matrixSize && lid in 0 until matrixSize) {
            "connection id out of range: rid=$rid, lid=$lid, matrixSize=$matrixSize"
        }
        val index = rid * matrixSize + lid
        require(index in 0 until entryCount) {
            "connection index out of range: index=$index, size=$entryCount, matrixSize=$matrixSize"
        }
        return index
    }

    private const val MAGIC = 0xCDAB
    private const val INDEX_MAGIC = 0x4B434931
    private const val INDEX_VERSION = 1
    private const val HEADER_SIZE = 8
    private const val RANK_BLOCK_BYTES = 32
    private const val CACHE_SIZE = 1024
    private const val CACHE_MASK = CACHE_SIZE - 1
}
