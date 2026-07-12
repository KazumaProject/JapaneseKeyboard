package com.kazumaproject.markdownhelperkeyboard.converter.ngram

import com.kazumaproject.graph.Node
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

class PackedSystemNgramDictionary private constructor(
    private val bytes: ByteArray,
) : SystemNgramDictionary {
    private val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    override val ruleCount: Int = buffer.getInt(8)
    override val storageBytes: Int = bytes.size
    private val contextCount = buffer.getInt(12)
    private val signatureCount = buffer.getInt(20)
    private val maxKeyBytes = buffer.getInt(24)
    private val blockSize = buffer.getInt(28)
    private val signaturesOffset = buffer.getInt(32)
    private val contextsOffset = buffer.getInt(36)
    private val bucketOffsetsOffset = buffer.getInt(40)
    private val hashEntriesOffset = buffer.getInt(44)
    private val blockOffsetsOffset = buffer.getInt(48)
    private val recordsOffset = buffer.getInt(52)
    private val bucketCount = buffer.getInt(64)
    private val bucketBits = Integer.numberOfTrailingZeros(bucketCount)
    private val scratch = ThreadLocal.withInitial {
        Scratch(ByteArray(maxKeyBytes.coerceAtLeast(1)), ByteArray(maxKeyBytes.coerceAtLeast(1)))
    }

    init {
        verify()
    }

    override fun matches(
        node0: Node,
        node1: Node,
        node2: Node?,
        node3: Node?,
        node4: Node?,
    ): Boolean {
        if (node0.tango == "BOS" || node1.tango == "EOS") return false
        val local = checkNotNull(scratch.get())
        var signatureIndex = 0
        while (signatureIndex < signatureCount) {
            val signature = buffer.getInt(signaturesOffset + signatureIndex * 4)
            val order = signature and 0x7
            if (order >= 3 && (node2 == null || node2.tango == "EOS")) {
                signatureIndex++
                continue
            }
            if (order >= 4 && (node3 == null || node3.tango == "EOS")) {
                signatureIndex++
                continue
            }
            if (order >= 5 && (node4 == null || node4.tango == "EOS")) {
                signatureIndex++
                continue
            }
            val queryLength = encodeQuery(
                target = local.query,
                signature = signature,
                node0 = node0,
                node1 = node1,
                node2 = node2,
                node3 = node3,
                node4 = node4,
            )
            if (queryLength >= 0 && contains(local.query, queryLength, local.record)) return true
            signatureIndex++
        }
        return false
    }

    private fun contains(query: ByteArray, queryLength: Int, record: ByteArray): Boolean {
        val hash = hash64(query, queryLength)
        val bucket = (hash ushr (64 - bucketBits)).toInt() and (bucketCount - 1)
        val wanted = hash and LOW_48_MASK
        var low = buffer.getInt(bucketOffsetsOffset + bucket * 4)
        var high = buffer.getInt(bucketOffsetsOffset + (bucket + 1) * 4) - 1
        var first = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val value = readUInt48(hashEntriesOffset + middle * HASH_ENTRY_SIZE)
            when {
                value < wanted -> low = middle + 1
                value > wanted -> high = middle - 1
                else -> {
                    first = middle
                    high = middle - 1
                }
            }
        }
        if (first < 0) return false
        var entry = first
        val bucketEnd = buffer.getInt(bucketOffsetsOffset + (bucket + 1) * 4)
        while (entry < bucketEnd) {
            val offset = hashEntriesOffset + entry * HASH_ENTRY_SIZE
            if (readUInt48(offset) != wanted) break
            val recordId = buffer.getInt(offset + 6)
            val length = decodeRecord(recordId, record)
            if (length == queryLength && regionEquals(query, record, length)) return true
            entry++
        }
        return false
    }

    private fun decodeRecord(recordId: Int, target: ByteArray): Int {
        val block = recordId / blockSize
        val withinBlock = recordId % blockSize
        var position = recordsOffset + buffer.getInt(blockOffsetsOffset + block * 4)
        var packed = readUVarint(position)
        var length = packed.toInt()
        position = (packed ushr 32).toInt()
        require(length <= target.size)
        bytes.copyInto(target, 0, position, position + length)
        position += length
        var index = 1
        while (index <= withinBlock) {
            packed = readUVarint(position)
            val prefix = packed.toInt()
            position = (packed ushr 32).toInt()
            packed = readUVarint(position)
            val suffix = packed.toInt()
            position = (packed ushr 32).toInt()
            length = prefix + suffix
            require(prefix >= 0 && length <= target.size)
            bytes.copyInto(target, prefix, position, position + suffix)
            position += suffix
            index++
        }
        return length
    }

    private fun encodeQuery(
        target: ByteArray,
        signature: Int,
        node0: Node,
        node1: Node,
        node2: Node?,
        node3: Node?,
        node4: Node?,
    ): Int {
        var position = 0
        if (target.size < 2) return -1
        target[position++] = signature.toByte()
        target[position++] = (signature ushr 8).toByte()
        val order = signature and 0x7
        var feature = 0
        while (feature < order) {
            val node = when (feature) {
                0 -> node0
                1 -> node1
                2 -> node2 ?: return -1
                3 -> node3 ?: return -1
                else -> node4 ?: return -1
            }
            when ((signature ushr (3 + feature * 2)) and 0x3) {
                KIND_WORD -> {
                    val utf8Length = utf8Length(node.tango)
                    position = writeUVarint(target, position, utf8Length)
                    if (position < 0 || position + utf8Length > target.size) return -1
                    position = writeUtf8(node.tango, target, position)
                }
                KIND_POS -> {
                    val contextId = node.l.toInt()
                    if (contextId !in 0 until contextCount || position >= target.size) return -1
                    target[position++] = bytes[contextsOffset + contextId]
                }
                KIND_ANY -> Unit
                else -> return -1
            }
            feature++
        }
        return position
    }

    private fun writeUVarint(target: ByteArray, start: Int, value: Int): Int {
        var position = start
        var remaining = value
        while (remaining >= 0x80) {
            if (position >= target.size) return -1
            target[position++] = ((remaining and 0x7f) or 0x80).toByte()
            remaining = remaining ushr 7
        }
        if (position >= target.size) return -1
        target[position++] = remaining.toByte()
        return position
    }

    private fun readUVarint(start: Int): Long {
        var position = start
        var shift = 0
        var value = 0
        while (true) {
            require(position < bytes.size && shift <= 28)
            val byte = bytes[position++].toInt() and 0xff
            value = value or ((byte and 0x7f) shl shift)
            if (byte and 0x80 == 0) return (position.toLong() shl 32) or (value.toLong() and 0xffffffffL)
            shift += 7
        }
    }

    private fun utf8Length(value: String): Int {
        var length = 0
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when {
                char.code < 0x80 -> length++
                char.code < 0x800 -> length += 2
                char.isHighSurrogate() && index + 1 < value.length && value[index + 1].isLowSurrogate() -> {
                    length += 4
                    index++
                }
                else -> length += 3
            }
            index++
        }
        return length
    }

    private fun writeUtf8(value: String, target: ByteArray, start: Int): Int {
        var position = start
        var index = 0
        while (index < value.length) {
            val char = value[index]
            val codePoint = if (
                char.isHighSurrogate() && index + 1 < value.length && value[index + 1].isLowSurrogate()
            ) {
                Character.toCodePoint(char, value[++index])
            } else {
                char.code
            }
            when {
                codePoint < 0x80 -> target[position++] = codePoint.toByte()
                codePoint < 0x800 -> {
                    target[position++] = (0xc0 or (codePoint ushr 6)).toByte()
                    target[position++] = (0x80 or (codePoint and 0x3f)).toByte()
                }
                codePoint < 0x10000 -> {
                    target[position++] = (0xe0 or (codePoint ushr 12)).toByte()
                    target[position++] = (0x80 or ((codePoint ushr 6) and 0x3f)).toByte()
                    target[position++] = (0x80 or (codePoint and 0x3f)).toByte()
                }
                else -> {
                    target[position++] = (0xf0 or (codePoint ushr 18)).toByte()
                    target[position++] = (0x80 or ((codePoint ushr 12) and 0x3f)).toByte()
                    target[position++] = (0x80 or ((codePoint ushr 6) and 0x3f)).toByte()
                    target[position++] = (0x80 or (codePoint and 0x3f)).toByte()
                }
            }
            index++
        }
        return position
    }

    private fun hash64(value: ByteArray, length: Int): Long {
        var hash = FNV_OFFSET
        var index = 0
        while (index < length) {
            hash = (hash xor (value[index].toLong() and 0xffL)) * FNV_PRIME
            index++
        }
        hash = (hash xor (hash ushr 33)) * MIX_1
        hash = (hash xor (hash ushr 33)) * MIX_2
        return hash xor (hash ushr 33)
    }

    private fun readUInt48(offset: Int): Long {
        var value = 0L
        repeat(6) { index -> value = value or ((bytes[offset + index].toLong() and 0xffL) shl (index * 8)) }
        return value
    }

    private fun regionEquals(left: ByteArray, right: ByteArray, length: Int): Boolean {
        var index = 0
        while (index < length) {
            if (left[index] != right[index]) return false
            index++
        }
        return true
    }

    private fun verify() {
        require(bytes.size >= HEADER_SIZE) { "Truncated n-gram header" }
        require(buffer.getInt(0) == MAGIC) { "Invalid n-gram magic" }
        require(buffer.getInt(4) == VERSION) { "Unsupported n-gram version" }
        require(buffer.getInt(56) == bytes.size) { "Invalid n-gram file size" }
        require(ruleCount > 0) { "Invalid n-gram rule count" }
        require(contextCount > 0) { "Invalid n-gram context count" }
        val posCount = buffer.getInt(16)
        require(posCount in 1..255) { "Invalid n-gram POS count" }
        require(signatureCount in 1..ruleCount) { "Invalid n-gram signature count" }
        require(bucketCount in 256..MAX_BUCKET_COUNT && bucketCount.countOneBits() == 1) {
            "Invalid n-gram bucket count"
        }
        require(blockSize == BLOCK_SIZE && maxKeyBytes > 0) { "Invalid n-gram record parameters" }
        val blockCount = buffer.getInt(68)
        require(blockCount == (ruleCount + blockSize - 1) / blockSize) { "Invalid n-gram block count" }

        val expectedContextsOffset = signaturesOffset.toLong() + signatureCount.toLong() * 4L
        val expectedBucketOffsetsOffset = (expectedContextsOffset + contextCount + 3L) and -4L
        val expectedHashEntriesOffset = expectedBucketOffsetsOffset + (bucketCount.toLong() + 1L) * 4L
        val expectedBlockOffsetsOffset = expectedHashEntriesOffset + ruleCount.toLong() * HASH_ENTRY_SIZE
        val expectedRecordsOffset = expectedBlockOffsetsOffset + (blockCount.toLong() + 1L) * 4L
        require(signaturesOffset == HEADER_SIZE) { "Invalid n-gram signatures offset" }
        require(contextsOffset.toLong() == expectedContextsOffset) { "Invalid n-gram contexts offset" }
        require(bucketOffsetsOffset.toLong() == expectedBucketOffsetsOffset) { "Invalid n-gram bucket offset" }
        require(hashEntriesOffset.toLong() == expectedHashEntriesOffset) { "Invalid n-gram hash offset" }
        require(blockOffsetsOffset.toLong() == expectedBlockOffsetsOffset) { "Invalid n-gram block offset" }
        require(recordsOffset.toLong() == expectedRecordsOffset && recordsOffset <= bytes.size) {
            "Invalid n-gram records offset"
        }
        val crc = CRC32().apply { update(bytes, HEADER_SIZE, bytes.size - HEADER_SIZE) }
        require(buffer.getInt(60).toUInt().toLong() == crc.value) { "Invalid n-gram checksum" }

        repeat(signatureCount) { index ->
            val signature = buffer.getInt(signaturesOffset + index * 4)
            val order = signature and 0x7
            require(order in 2..5) { "Invalid n-gram signature order" }
            repeat(order) { feature ->
                require(((signature ushr (3 + feature * 2)) and 0x3) in KIND_WORD..KIND_ANY) {
                    "Invalid n-gram signature kind"
                }
            }
        }
        repeat(contextCount) { index ->
            require((bytes[contextsOffset + index].toInt() and 0xff) in 1..posCount) {
                "Invalid n-gram context POS"
            }
        }

        var previousEntry = 0
        repeat(bucketCount + 1) { bucket ->
            val entry = buffer.getInt(bucketOffsetsOffset + bucket * 4)
            require(entry in previousEntry..ruleCount) { "Invalid n-gram bucket range" }
            if (bucket > 0) verifyHashBucket(previousEntry, entry)
            previousEntry = entry
        }
        require(previousEntry == ruleCount) { "Invalid n-gram hash entry count" }

        var previousBlockOffset = 0
        repeat(blockCount + 1) { block ->
            val offset = buffer.getInt(blockOffsetsOffset + block * 4)
            require(offset in previousBlockOffset..(bytes.size - recordsOffset)) {
                "Invalid n-gram block range"
            }
            if (block > 0) verifyRecordBlock(block - 1, previousBlockOffset, offset)
            previousBlockOffset = offset
        }
        require(recordsOffset + previousBlockOffset == bytes.size) { "Invalid n-gram records size" }
    }

    private fun verifyHashBucket(start: Int, end: Int) {
        var previousRemainder = -1L
        var previousRecord = -1
        for (entry in start until end) {
            val offset = hashEntriesOffset + entry * HASH_ENTRY_SIZE
            val remainder = readUInt48(offset)
            val record = buffer.getInt(offset + 6)
            require(record in 0 until ruleCount) { "Invalid n-gram record ID" }
            require(remainder > previousRemainder || remainder == previousRemainder && record > previousRecord) {
                "Unsorted n-gram hash bucket"
            }
            previousRemainder = remainder
            previousRecord = record
        }
    }

    private fun verifyRecordBlock(block: Int, relativeStart: Int, relativeEnd: Int) {
        var position = recordsOffset + relativeStart
        val end = recordsOffset + relativeEnd
        val recordsInBlock = minOf(blockSize, ruleCount - block * blockSize)
        var previousLength = 0
        repeat(recordsInBlock) { index ->
            if (index == 0) {
                val packed = readUVarint(position)
                val length = packed.toInt()
                position = (packed ushr 32).toInt()
                require(length in 1..maxKeyBytes && position + length <= end) { "Invalid n-gram record" }
                position += length
                previousLength = length
            } else {
                var packed = readUVarint(position)
                val prefix = packed.toInt()
                position = (packed ushr 32).toInt()
                packed = readUVarint(position)
                val suffix = packed.toInt()
                position = (packed ushr 32).toInt()
                val length = prefix.toLong() + suffix.toLong()
                require(prefix in 0..previousLength && length in 1..maxKeyBytes.toLong()) {
                    "Invalid front-coded n-gram record"
                }
                require(position.toLong() + suffix.toLong() <= end.toLong()) { "Truncated n-gram record" }
                position += suffix
                previousLength = length.toInt()
            }
        }
        require(position == end) { "Invalid n-gram block length" }
    }

    private data class Scratch(val query: ByteArray, val record: ByteArray)

    companion object {
        private const val MAGIC = 0x4A4B4E47
        private const val VERSION = 3
        private const val HEADER_SIZE = 80
        private const val BLOCK_SIZE = 16
        private const val MAX_BUCKET_COUNT = 65536
        private const val HASH_ENTRY_SIZE = 10
        private const val KIND_WORD = 1
        private const val KIND_POS = 2
        private const val KIND_ANY = 3
        private const val LOW_48_MASK = 0x0000ffffffffffffL
        private const val FNV_OFFSET = -3750763034362895579L
        private const val FNV_PRIME = 1099511628211L
        private const val MIX_1 = -49064778989728563L
        private const val MIX_2 = -4265267296055464877L

        /** Takes ownership of [bytes]; the reader never copies the dictionary. */
        fun read(bytes: ByteArray): PackedSystemNgramDictionary = PackedSystemNgramDictionary(bytes)
    }
}
