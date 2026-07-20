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
)

/** Loads the English dictionary's final arrays only when English conversion is first requested. */
object CompactEnglishDictionaryReader {
    private const val MAGIC = 0x4B444531 // KDE1
    private const val VERSION = 1

    fun read(descriptor: AssetFileDescriptor): CompactEnglishDictionary {
        require(descriptor.length > 0L) {
            "Compact English dictionary must be an uncompressed asset with a known length"
        }
        return FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
            read(
                channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.startOffset,
                    descriptor.length,
                ),
            )
        }
    }

    internal fun read(source: ByteBuffer): CompactEnglishDictionary {
        val input = Cursor(source.duplicate().order(ByteOrder.BIG_ENDIAN))
        require(input.readInt() == MAGIC) { "Invalid compact English dictionary magic" }
        require(input.readInt() == VERSION) { "Unsupported compact English dictionary version" }
        val readingLbs = input.readBitSet()
        val readingLeaf = input.readBitSet()
        val reading = LOUDSWithTermId(
            readingLbs,
            input.readCharArray(),
            readingLeaf,
            input.readIntArray(),
        )
        val wordLbs = input.readBitSet()
        val wordLeaf = input.readBitSet()
        val word = LOUDS(wordLbs, input.readCharArray(), wordLeaf)
        val token = TokenArray.fromArrays(
            wordCosts = input.readShortArray(),
            nodeIds = input.readIntArray(),
            bitvector = input.readBitSet(),
        )
        val result = CompactEnglishDictionary(
            readingTrie = reading,
            wordTrie = word,
            tokenArray = token,
            readingLbsIndex = input.readSuccinctBitVector(reading.LBS),
            readingLeafIndex = input.readSuccinctBitVector(reading.isLeaf),
            wordLbsIndex = input.readSuccinctBitVector(word.LBS),
            tokenIndex = input.readSuccinctBitVector(token.bitvector),
        )
        require(input.remaining == 0) {
            "Compact English dictionary has ${input.remaining} trailing bytes"
        }
        return result
    }

    private class Cursor(private val buffer: ByteBuffer) {
        val remaining: Int
            get() = buffer.remaining()

        fun readInt(): Int {
            requireRemaining(Int.SIZE_BYTES)
            return buffer.int
        }

        fun readBitSet(): BitSet = BitSet.valueOf(readLongArray())

        fun readSuccinctBitVector(bitSet: BitSet): SuccinctBitVector {
            val totalOnes = readInt()
            return SuccinctBitVector.fromPrecomputed(
                bitSet = bitSet,
                bigBlockRanks = readIntArray(),
                smallBlockRanks = readByteArray(),
                totalOnes = totalOnes,
            )
        }

        fun readCharArray(): CharArray {
            val size = readSizedCount(Char.SIZE_BYTES)
            return CharArray(size).also { take(size * Char.SIZE_BYTES).asCharBuffer().get(it) }
        }

        fun readIntArray(): IntArray {
            val size = readSizedCount(Int.SIZE_BYTES)
            return IntArray(size).also { take(size * Int.SIZE_BYTES).asIntBuffer().get(it) }
        }

        fun readShortArray(): ShortArray {
            val size = readSizedCount(Short.SIZE_BYTES)
            return ShortArray(size).also { take(size * Short.SIZE_BYTES).asShortBuffer().get(it) }
        }

        private fun readLongArray(): LongArray {
            val size = readSizedCount(Long.SIZE_BYTES)
            return LongArray(size).also { take(size * Long.SIZE_BYTES).asLongBuffer().get(it) }
        }

        private fun readByteArray(): ByteArray {
            val size = readSizedCount(Byte.SIZE_BYTES)
            return ByteArray(size).also(buffer::get)
        }

        private fun readSizedCount(elementBytes: Int): Int {
            val size = readInt()
            require(size >= 0 && size <= remaining / elementBytes) {
                "Invalid compact English array size: $size elements of $elementBytes bytes"
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
                "Truncated compact English dictionary: need=$byteCount remaining=${buffer.remaining()}"
            }
        }
    }
}
