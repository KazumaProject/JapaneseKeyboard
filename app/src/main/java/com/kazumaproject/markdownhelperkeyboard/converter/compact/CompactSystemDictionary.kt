package com.kazumaproject.markdownhelperkeyboard.converter.compact

import android.content.res.AssetFileDescriptor
import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.BitSet

enum class CompactDictionaryKind(val id: Int) {
    SYSTEM(0),
    SINGLE_KANJI(1),
    EMOJI(2),
    EMOTICON(3),
    SYMBOL(4),
    READING_CORRECTION(5),
    KOTOWAZA(6),
    ;

    companion object {
        fun fromId(id: Int): CompactDictionaryKind =
            entries.firstOrNull { it.id == id }
                ?: error("Unknown compact dictionary id: $id")
    }
}

data class CompactDictionaryTriple(
    val tangoTrie: LOUDS,
    val yomiTrie: LOUDSWithTermId,
    val tokenArray: TokenArray,
    val yomiLbsIndex: SuccinctBitVector,
    val yomiLeafIndex: SuccinctBitVector,
    val tokenIndex: SuccinctBitVector,
    val tangoLbsIndex: SuccinctBitVector,
)

data class CompactSystemDictionary(
    val dictionaries: Map<CompactDictionaryKind, CompactDictionaryTriple>,
) {
    fun get(kind: CompactDictionaryKind): CompactDictionaryTriple =
        dictionaries[kind] ?: error("Compact dictionary is missing: $kind")
}

/**
 * Loads the bundled system dictionary in its final runtime representation.
 *
 * The asset is generated at build time and stored uncompressed in the APK. Runtime loading maps
 * the asset and bulk-copies already packed primitive sections; it never uses ObjectInputStream,
 * inflates ZIP entries, or scans unpacked label/node arrays to pack them again.
 */
object CompactSystemDictionaryReader {
    private const val MAGIC = 0x4B445331 // KDS1
    private const val VERSION = 3

    fun read(descriptor: AssetFileDescriptor): CompactSystemDictionary {
        require(descriptor.length > 0L) {
            "Compact system dictionary must be an uncompressed asset with a known length"
        }
        val stream = FileInputStream(descriptor.fileDescriptor)
        return stream.channel.use { channel ->
            read(
                channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.startOffset,
                    descriptor.length,
                ),
            )
        }
    }

    internal fun read(source: ByteBuffer): CompactSystemDictionary {
        val input = Cursor(source.duplicate().order(ByteOrder.BIG_ENDIAN))
        require(input.readInt() == MAGIC) { "Invalid compact system dictionary magic" }
        require(input.readInt() == VERSION) {
            "Unsupported compact system dictionary version"
        }

        val dictionaryCount = input.readInt()
        require(dictionaryCount == CompactDictionaryKind.entries.size) {
            "Unexpected compact dictionary count: $dictionaryCount"
        }
        val dictionaries = LinkedHashMap<CompactDictionaryKind, CompactDictionaryTriple>(dictionaryCount)
        repeat(dictionaryCount) {
            val kind = CompactDictionaryKind.fromId(input.readInt())
            require(kind !in dictionaries) { "Duplicate compact dictionary: $kind" }
            dictionaries[kind] = input.readDictionaryTriple()
        }
        require(input.remaining == 0) {
            "Compact system dictionary has ${input.remaining} trailing bytes"
        }
        return CompactSystemDictionary(dictionaries)
    }

    private class Cursor(private val buffer: ByteBuffer) {
        val remaining: Int
            get() = buffer.remaining()

        fun readInt(): Int {
            requireRemaining(Int.SIZE_BYTES)
            return buffer.int
        }

        fun readDictionaryTriple(): CompactDictionaryTriple {
            val tangoTrie = LOUDS.fromPacked(
                LBS = readBitSet(),
                isLeaf = readBitSet(),
                labels = readPackedCharArray(),
            )
            val yomiTrie = LOUDSWithTermId.fromPacked(
                LBS = readBitSet(),
                isLeaf = readBitSet(),
                labels = readPackedCharArray(),
                termIds = readPackedIntArray(),
            )
            val tokenArray = TokenArray.fromPacked(
                posTableIndices = readShortArray(),
                wordCosts = readShortArray(),
                nodeIds = readPackedIntArray(),
                bitvector = readBitSet(),
                leftIds = readShortArray(),
                rightIds = readShortArray(),
            )
            return CompactDictionaryTriple(
                tangoTrie = tangoTrie,
                yomiTrie = yomiTrie,
                tokenArray = tokenArray,
                yomiLbsIndex = readSuccinctBitVector(yomiTrie.LBS),
                yomiLeafIndex = readSuccinctBitVector(yomiTrie.isLeaf),
                tokenIndex = readSuccinctBitVector(tokenArray.bitvector),
                tangoLbsIndex = readSuccinctBitVector(tangoTrie.LBS),
            )
        }

        fun readBitSet(): BitSet = BitSet.valueOf(readLongArray())

        fun readSuccinctBitVector(bitSet: BitSet): SuccinctBitVector {
            val totalOnes = readInt()
            val bigBlockRanks = readIntArray()
            val smallBlockRanks = readByteArray()
            return SuccinctBitVector.fromPrecomputed(
                bitSet,
                bigBlockRanks,
                smallBlockRanks,
                totalOnes,
            )
        }

        fun readPackedCharArray(): PackedCharArray {
            val alphabet = readCharArray()
            return PackedCharArray.fromEncoded(alphabet, readPackedIntArray())
        }

        fun readPackedIntArray(): PackedIntArray {
            val size = readInt()
            val minimum = readInt()
            val bitWidth = readInt()
            val words = readLongArray()
            return PackedIntArray.fromEncoded(size, minimum, bitWidth, words)
        }

        fun readShortArray(): ShortArray {
            val size = readSizedCount(Short.SIZE_BYTES)
            return ShortArray(size).also { values ->
                take(size * Short.SIZE_BYTES).asShortBuffer().get(values)
            }
        }

        private fun readIntArray(): IntArray {
            val size = readSizedCount(Int.SIZE_BYTES)
            return IntArray(size).also { values ->
                take(size * Int.SIZE_BYTES).asIntBuffer().get(values)
            }
        }

        private fun readByteArray(): ByteArray {
            val size = readSizedCount(Byte.SIZE_BYTES)
            return ByteArray(size).also { values -> buffer.get(values) }
        }

        private fun readCharArray(): CharArray {
            val size = readSizedCount(Char.SIZE_BYTES)
            return CharArray(size).also { values ->
                take(size * Char.SIZE_BYTES).asCharBuffer().get(values)
            }
        }

        private fun readLongArray(): LongArray {
            val size = readSizedCount(Long.SIZE_BYTES)
            return LongArray(size).also { values ->
                take(size * Long.SIZE_BYTES).asLongBuffer().get(values)
            }
        }

        private fun readSizedCount(elementBytes: Int): Int {
            val size = readInt()
            require(size >= 0 && size <= remaining / elementBytes) {
                "Invalid compact dictionary array size: $size elements of $elementBytes bytes"
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
                "Truncated compact system dictionary: need=$byteCount remaining=${buffer.remaining()}"
            }
        }
    }
}
