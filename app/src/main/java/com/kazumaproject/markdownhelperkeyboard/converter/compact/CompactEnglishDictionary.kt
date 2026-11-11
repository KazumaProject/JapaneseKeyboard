package com.kazumaproject.markdownhelperkeyboard.converter.compact

import android.content.res.AssetFileDescriptor
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.english.louds.LOUDS
import com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id.LOUDSWithTermId
import com.kazumaproject.markdownhelperkeyboard.converter.english.tokenArray.TokenArray
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.BitSet

data class CompactEnglishDictionary(
    val readingTrie: LOUDSWithTermId,
    val wordTrie: LOUDS,
    val tokenArray: TokenArray,
    val readingLbsIndex: SuccinctBitVector,
    val readingLeafIndex: SuccinctBitVector,
    val wordLbsIndex: SuccinctBitVector,
    val tokenIndex: SuccinctBitVector,
    val wordCompletionBounds: EnglishCompletionBounds? = null,
    val phraseCompletionBounds: EnglishCompletionBounds? = null,
)

fun interface EnglishCompletionBounds {
    operator fun get(labelIndex: Int): Int
}

/** Maps the bundled English dictionary arrays and precomputed lookup index. */
internal object BundledEnglishDictionaryReader {
    private const val MAGIC = 0x4B454931 // KEI1

    fun readIndex(
        descriptor: AssetFileDescriptor,
        reading: LOUDSWithTermId,
        word: LOUDS,
        token: TokenArray,
    ): CompactEnglishDictionary {
        val input = Cursor(descriptor)
        require(input.readInt() == MAGIC) { "Invalid English lookup index magic" }
        // The build indexes the used words of each BitSet. A serialized BitSet may
        // also retain spare words that would make its runtime rank table larger.
        reading.LBS = reading.LBS.withoutSpareCapacity()
        reading.isLeaf = reading.isLeaf.withoutSpareCapacity()
        word.LBS = word.LBS.withoutSpareCapacity()
        word.isLeaf = word.isLeaf.withoutSpareCapacity()
        token.bitvector = token.bitvector.withoutSpareCapacity()
        val result = CompactEnglishDictionary(
            readingTrie = reading,
            wordTrie = word,
            tokenArray = token,
            readingLbsIndex = input.readSuccinctBitVector(reading.LBS),
            readingLeafIndex = input.readSuccinctBitVector(reading.isLeaf),
            wordLbsIndex = input.readSuccinctBitVector(word.LBS),
            tokenIndex = input.readSuccinctBitVector(token.bitvector),
            wordCompletionBounds = input.readBounds(reading.labels.size),
            phraseCompletionBounds = input.readBounds(reading.labels.size),
        )
        require(input.remaining == 0) {
            "English lookup index has ${input.remaining} trailing bytes"
        }
        return result
    }

    private fun BitSet.withoutSpareCapacity(): BitSet =
        if (size() == (length() + Long.SIZE_BITS - 1) / Long.SIZE_BITS * Long.SIZE_BITS) this
        else BitSet.valueOf(toLongArray())

    fun readReading(descriptor: AssetFileDescriptor): LOUDSWithTermId = read(descriptor, 0x4B455231) {
        val lbs = readBitSet()
        val leaves = readBitSet()
        LOUDSWithTermId(lbs, readCharArray(), leaves, readIntArray())
    }

    fun readWord(descriptor: AssetFileDescriptor): LOUDS = read(descriptor, 0x4B455731) {
        val lbs = readBitSet()
        val leaves = readBitSet()
        LOUDS(lbs, readCharArray(), leaves)
    }

    fun readToken(descriptor: AssetFileDescriptor): TokenArray = read(descriptor, 0x4B455431) {
        TokenArray.fromArrays(readShortArray(), readIntArray(), readBitSet())
    }

    private inline fun <T> read(
        descriptor: AssetFileDescriptor,
        magic: Int,
        decode: Cursor.() -> T,
    ): T {
        val input = Cursor(descriptor)
        require(input.readInt() == magic) { "Invalid bundled English dictionary header" }
        return input.decode().also {
            require(input.remaining == 0) { "Unexpected trailing English dictionary data" }
        }
    }

    private class Cursor(descriptor: AssetFileDescriptor) {
        private val buffer: ByteBuffer = FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
            require(descriptor.length > 0L) { "English dictionary asset must have a known length" }
            channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.length)
        }.order(ByteOrder.BIG_ENDIAN)

        val remaining: Int
            get() = buffer.remaining()

        fun readInt(): Int {
            requireRemaining(Int.SIZE_BYTES)
            return buffer.int
        }

        fun readSuccinctBitVector(bitSet: BitSet): SuccinctBitVector {
            val totalOnes = readInt()
            return SuccinctBitVector.fromPrecomputed(
                bitSet = bitSet,
                bigBlockRanks = readIntArray(),
                smallBlockRanks = readByteArray(),
                totalOnes = totalOnes,
            )
        }

        fun readBitSet(): BitSet {
            val size = readSizedCount(Long.SIZE_BYTES)
            val words = LongArray(size).also { take(size * Long.SIZE_BYTES).asLongBuffer().get(it) }
            return BitSet.valueOf(words)
        }

        fun readCharArray(): CharArray {
            val size = readSizedCount(Char.SIZE_BYTES)
            return CharArray(size).also { take(size * Char.SIZE_BYTES).asCharBuffer().get(it) }
        }

        fun readShortArray(): ShortArray {
            val size = readSizedCount(Short.SIZE_BYTES)
            return ShortArray(size).also { take(size * Short.SIZE_BYTES).asShortBuffer().get(it) }
        }

        fun readIntArray(): IntArray {
            val size = readSizedCount(Int.SIZE_BYTES)
            return IntArray(size).also { take(size * Int.SIZE_BYTES).asIntBuffer().get(it) }
        }

        fun readBounds(labelCount: Int): EnglishCompletionBounds {
            val ranks = readIntArray()
            require(ranks.size <= 65_536) { "Invalid English completion rank table" }
            val elementBytes = when {
                ranks.isEmpty() -> Int.SIZE_BYTES
                ranks.size <= 256 -> Byte.SIZE_BYTES
                else -> Short.SIZE_BYTES
            }
            val size = readSizedCount(elementBytes)
            require(size == labelCount) { "English completion bounds do not match reading labels" }
            val data = take(size * elementBytes)
            return when (elementBytes) {
                Int.SIZE_BYTES -> {
                    val bounds = data.asIntBuffer()
                    EnglishCompletionBounds { bounds.get(it) }
                }
                Byte.SIZE_BYTES -> EnglishCompletionBounds { ranks[data.get(it).toInt() and 0xff] }
                else -> {
                    val bounds = data.asShortBuffer()
                    EnglishCompletionBounds { ranks[bounds.get(it).toInt() and 0xffff] }
                }
            }
        }

        private fun readByteArray(): ByteArray {
            val size = readSizedCount(Byte.SIZE_BYTES)
            return ByteArray(size).also(buffer::get)
        }

        private fun readSizedCount(elementBytes: Int): Int {
            val size = readInt()
            require(size >= 0 && size <= remaining / elementBytes) {
                "Invalid English dictionary array size: $size elements of $elementBytes bytes"
            }
            return size
        }

        private fun take(byteCount: Int): ByteBuffer {
            requireRemaining(byteCount)
            val result = buffer.slice().order(buffer.order())
            result.limit(byteCount)
            buffer.position(buffer.position() + byteCount)
            return result
        }

        private fun requireRemaining(byteCount: Int) {
            require(byteCount >= 0 && buffer.remaining() >= byteCount) {
                "Truncated English dictionary data: need=$byteCount remaining=${buffer.remaining()}"
            }
        }
    }
}
