package com.kazumaproject.markdownhelperkeyboard.converter.compact

import android.content.res.AssetFileDescriptor
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.english.louds.LOUDS
import com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id.LOUDSWithTermId
import com.kazumaproject.markdownhelperkeyboard.converter.english.tokenArray.TokenArray
import java.io.DataInputStream
import java.io.FileInputStream
import java.io.InputStream
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

/** Maps the precomputed lookup index for the serialized English dictionary. */
object EnglishLookupIndexReader {
    private const val MAGIC = 0x4B454931 // KEI1

    fun read(
        descriptor: AssetFileDescriptor,
        reading: LOUDSWithTermId,
        word: LOUDS,
        token: TokenArray,
    ): CompactEnglishDictionary {
        require(descriptor.length > 0L) {
            "English lookup index must be an uncompressed asset with a known length"
        }
        val source = FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
            channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.length)
        }

        val input = Cursor(source.order(ByteOrder.BIG_ENDIAN))
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

    private class Cursor(private val buffer: ByteBuffer) {
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
                "Invalid English lookup array size: $size elements of $elementBytes bytes"
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
                "Truncated English lookup index: need=$byteCount remaining=${buffer.remaining()}"
            }
        }
    }
}

/** Reads the bundled dictionary arrays without Java object deserialization. */
internal object EnglishDictionaryAssetReader {
    fun readReading(source: InputStream): LOUDSWithTermId = EntryInput(source, 0x4B455231).run {
        val lbs = readBitSet()
        val leaves = readBitSet()
        LOUDSWithTermId(lbs, readChars(), leaves, readInts()).also { requireEnd() }
    }

    fun readWord(source: InputStream): LOUDS = EntryInput(source, 0x4B455731).run {
        val lbs = readBitSet()
        val leaves = readBitSet()
        LOUDS(lbs, readChars(), leaves).also { requireEnd() }
    }

    fun readToken(source: InputStream): TokenArray = EntryInput(source, 0x4B455431).run {
        TokenArray.fromArrays(readShorts(), readInts(), readBitSet()).also { requireEnd() }
    }

    private class EntryInput(source: InputStream, magic: Int) {
        private val input = DataInputStream(source)
        private val block = ByteBuffer.allocate(64 * 1024).order(ByteOrder.BIG_ENDIAN)

        init {
            require(input.readInt() == magic) { "Invalid bundled English dictionary header" }
        }

        fun readBitSet(): BitSet {
            val words = LongArray(readCount(Long.SIZE_BYTES))
            readBlocks(words.size, Long.SIZE_BYTES) { offset, count ->
                block.asLongBuffer().get(words, offset, count)
            }
            return BitSet.valueOf(words)
        }

        fun readChars(): CharArray = CharArray(readCount(Char.SIZE_BYTES)).also { values ->
            readBlocks(values.size, Char.SIZE_BYTES) { offset, count ->
                block.asCharBuffer().get(values, offset, count)
            }
        }

        fun readInts(): IntArray = IntArray(readCount(Int.SIZE_BYTES)).also { values ->
            readBlocks(values.size, Int.SIZE_BYTES) { offset, count ->
                block.asIntBuffer().get(values, offset, count)
            }
        }

        fun readShorts(): ShortArray = ShortArray(readCount(Short.SIZE_BYTES)).also { values ->
            readBlocks(values.size, Short.SIZE_BYTES) { offset, count ->
                block.asShortBuffer().get(values, offset, count)
            }
        }

        fun requireEnd() {
            require(input.read() == -1) { "Unexpected trailing English dictionary data" }
        }

        private fun readCount(width: Int): Int = input.readInt().also {
            require(it in 0..(64 * 1024 * 1024 / width)) { "Invalid English dictionary array size: $it" }
        }

        private inline fun readBlocks(size: Int, width: Int, copy: (Int, Int) -> Unit) {
            var offset = 0
            while (offset < size) {
                val count = minOf(size - offset, block.capacity() / width)
                input.readFully(block.array(), 0, count * width)
                copy(offset, count)
                offset += count
            }
        }
    }
}
