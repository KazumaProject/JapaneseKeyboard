// Adapted from kotlin-kana-kanji-converter at f4c7151 (MIT). See the bundled English dictionary notices.
package com.kazumaproject.markdownhelperkeyboard.converter.english.phrase

import android.content.res.AssetFileDescriptor
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.zip.CRC32

private const val MAGIC = 0x45524F4C // EROL
private const val HEADER_SIZE = 40
private const val MAX_INDEX_SIZE = 16 * 1024 * 1024
private const val ROW_SIZE = Int.SIZE_BYTES + Short.SIZE_BYTES
private const val BLOCK_SIZE = 32
private const val MAX_KEY_BYTES = 1_048_576

/** Reads EROL v1 and the independently searchable, front-coded UTF-8 blocks in v2. */
internal class RuntimeGrammarRoles private constructor(
    private val data: ByteBuffer,
    validation: ByteBuffer,
    validateAllRecords: Boolean,
) : GrammarRoles {
    private val version = data.getInt(4)
    private var blockKeys = IntArray(0)
    private var blockLengths = IntArray(0)
    private var maxKeyBytes = 0
    private val maskCount = data.getInt(20)
    private val rowCount = data.getInt(24)
    private val blockCount = (rowCount + BLOCK_SIZE - 1) / BLOCK_SIZE
    private val masks = data.getInt(28)
    private val rows = data.getInt(32)
    private val text = data.getInt(36)
    private val names: List<String>
    private val words: Int

    init {
        val count = validation.getInt(16)
        require(count in 1..1024 && maskCount in 1..65_536 && rowCount in 1..validation.capacity() / 4) { "Invalid role index counts" }
        words = (count + 63) / 64
        require(masks in HEADER_SIZE..validation.capacity() && rows in masks..validation.capacity() &&
            text in rows..validation.capacity() && rows.toLong() == masks + maskCount.toLong() * words * Long.SIZE_BYTES &&
            text.toLong() == rows + (if (version == 1) rowCount.toLong() * ROW_SIZE + Int.SIZE_BYTES
                else ((rowCount.toLong() + BLOCK_SIZE - 1) / BLOCK_SIZE + 1) * Int.SIZE_BYTES)) { "Invalid role index sections" }
        val reader = validation.duplicate().apply {
            position(HEADER_SIZE)
            limit(masks)
        }
        names = List(count) {
            require(reader.remaining() >= Short.SIZE_BYTES) { "Truncated category names" }
            val size = reader.short.toInt() and 0xffff
            require(size in 1..reader.remaining()) { "Invalid category name length" }
            ByteArray(size).also { reader.get(it) }.toString(Charsets.UTF_8)
        }
        require(!reader.hasRemaining() && names.toSet().size == count &&
            names.all { it.matches(CATEGORY_NAME) }) { "Invalid category names" }
        if (count % 64 != 0) {
            for (id in 0 until maskCount) {
                require(validation.getLong(maskStart(id) + (words - 1) * Long.SIZE_BYTES) ushr (count % 64) == 0L) {
                    "Invalid category bit"
                }
            }
        }
        if (version == 2) {
            validateBlockOffsets(validation)
            if (validateAllRecords) validateBlocks(validation)
        } else {
            require(validation.getInt(rows) == 0 && validation.getInt(rows + rowCount * ROW_SIZE) == validation.capacity() - text) {
                "Invalid role index text length"
            }
            var last = text
            for (i in 0 until rowCount) {
                val row = rows + i * ROW_SIZE
                val start = text + validation.getInt(row)
                val end = text + validation.getInt(row + ROW_SIZE)
                require(start in text until validation.capacity() &&
                    end in (start + 1)..validation.capacity() && (validation.getShort(row + Int.SIZE_BYTES).toInt() and 0xffff) < maskCount) { "Invalid role index row" }
                if (i > 0) {
                    var prefix = 0
                    while (prefix < minOf(start - last, end - start) && validation.get(last + prefix) == validation.get(start + prefix)) {
                        prefix++
                    }
                    require(prefix < end - start &&
                        (prefix == start - last ||
                            (validation.get(last + prefix).toInt() and 255) < (validation.get(start + prefix).toInt() and 255))) {
                        "Invalid or unordered role index key"
                    }
                }
                last = start
            }
        }
    }

    override fun lookup(surface: String, normalized: String): Set<String> {
        val bits = LongArray(words)
        collect(normalized, bits)
        if (surface != normalized) collect(surface, bits)
        return decodeRoles(bits, names)
    }

    private fun collect(surface: String, bits: LongArray) {
        val key = surface.toByteArray(Charsets.UTF_8)
        if (version == 2) {
            collectBlock(key, bits)
            return
        }
        var low = 0
        var high = rowCount - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val start = keyStart(mid)
            val order = compareKey(data, key, start, keyStart(mid + 1) - start)
            when {
                order < 0 -> high = mid - 1
                order > 0 -> low = mid + 1
                else -> {
                    val mask = maskStart(maskId(rows + mid * ROW_SIZE))
                    for (word in bits.indices) bits[word] = bits[word] or data.getLong(mask + word * Long.SIZE_BYTES)
                    return
                }
            }
        }
    }

    private fun validateBlockOffsets(validation: ByteBuffer) {
        require(validation.getInt(rows) == 0 && validation.getInt(rows + blockCount * Int.SIZE_BYTES) == validation.capacity() - text) {
            "Invalid block directory"
        }
        var previous = 0
        for (block in 1..blockCount) {
            val offset = validation.getInt(rows + block * Int.SIZE_BYTES)
            require(offset > previous && offset <= validation.capacity() - text) { "Invalid block range" }
            previous = offset
        }
    }

    private fun validateBlocks(validation: ByteBuffer) {
        blockKeys = IntArray(blockCount)
        blockLengths = IntArray(blockCount)
        var previous = ByteArray(64)
        var current = ByteArray(64)
        var previousLength = 0
        val reader = validation.duplicate()
        for (block in 0 until blockCount) {
            val start = text + validation.getInt(rows + block * Int.SIZE_BYTES)
            val end = text + validation.getInt(rows + (block + 1) * Int.SIZE_BYTES)
            reader.limit(end)
            reader.position(start)
            repeat(minOf(BLOCK_SIZE, rowCount - block * BLOCK_SIZE)) { index ->
                val prefix = reader.readUnsigned()
                val suffix = reader.readUnsigned()
                require((index != 0 || prefix == 0) && prefix <= previousLength &&
                    suffix in 1..MAX_KEY_BYTES && prefix <= MAX_KEY_BYTES - suffix &&
                    reader.remaining() >= Short.SIZE_BYTES && suffix <= reader.remaining() - Short.SIZE_BYTES) {
                    "Invalid front-coded key"
                }
                val mask = reader.short.toInt() and 0xffff
                require(mask < maskCount) { "Invalid block mask" }
                if (index == 0) {
                    blockKeys[block] = reader.position()
                    blockLengths[block] = suffix
                }
                val length = prefix + suffix
                if (current.size < length) current = ByteArray(maxOf(length, current.size * 2))
                previous.copyInto(current, 0, 0, prefix)
                reader.get(current, prefix, suffix)
                require(previousLength == 0 || compareBytes(previous, previousLength, current, length) < 0) {
                    "Unordered block key"
                }
                val swap = previous
                previous = current
                current = swap
                previousLength = length
                maxKeyBytes = maxOf(maxKeyBytes, length)
            }
            require(!reader.hasRemaining()) { "Trailing block records" }
        }
    }

    private fun collectBlock(key: ByteArray, bits: LongArray) {
        var low = 0
        var high = blockCount - 1
        val reader = data.duplicate()
        fun seekBlock(block: Int) {
            reader.limit(text + data.getInt(rows + (block + 1) * Int.SIZE_BYTES))
            reader.position(text + data.getInt(rows + block * Int.SIZE_BYTES))
        }
        while (low <= high) {
            val mid = (low + high) ushr 1
            val order = if (blockKeys.isNotEmpty()) {
                compareKey(data, key, blockKeys[mid], blockLengths[mid])
            } else {
                seekBlock(mid)
                require(reader.readUnsigned() == 0) { "Invalid first key prefix" }
                val length = reader.readUnsigned()
                require(length in 1..MAX_KEY_BYTES && length <= reader.remaining() - Short.SIZE_BYTES) {
                    "Invalid first key length"
                }
                compareKey(data, key, reader.position() + Short.SIZE_BYTES, length)
            }
            if (order < 0) high = mid - 1 else low = mid + 1
        }
        if (high < 0) return
        seekBlock(high)
        var decoded = ByteArray(maxOf(64, maxKeyBytes))
        var decodedLength = 0
        repeat(minOf(BLOCK_SIZE, rowCount - high * BLOCK_SIZE)) {
            val prefix = reader.readUnsigned()
            val suffix = reader.readUnsigned()
            require(prefix <= decodedLength && suffix in 1..MAX_KEY_BYTES && prefix <= MAX_KEY_BYTES - suffix &&
                reader.remaining() >= Short.SIZE_BYTES && suffix <= reader.remaining() - Short.SIZE_BYTES) {
                "Invalid front-coded key"
            }
            val mask = reader.short.toInt() and 0xffff
            require(mask < maskCount) { "Invalid block mask" }
            decodedLength = prefix + suffix
            if (decoded.size < decodedLength) decoded = decoded.copyOf(decodedLength)
            reader.get(decoded, prefix, suffix)
            val order = compareBytes(key, key.size, decoded, decodedLength)
            if (order == 0) {
                val start = maskStart(mask)
                for (word in bits.indices) bits[word] = bits[word] or data.getLong(start + word * Long.SIZE_BYTES)
                return
            }
            if (order < 0) return
        }
    }

    private fun keyStart(index: Int) = text + data.getInt(rows + index * ROW_SIZE)
    private fun maskId(row: Int) = data.getShort(row + Int.SIZE_BYTES).toInt() and 0xffff
    private fun maskStart(id: Int) = masks + id * words * Long.SIZE_BYTES

    companion object {
        private val CATEGORY_NAME = Regex("[a-z][a-z0-9_]*")

        fun read(descriptor: AssetFileDescriptor): RuntimeGrammarRoles {
            val length = descriptor.length
            require(length in HEADER_SIZE.toLong()..MAX_INDEX_SIZE.toLong()) { "Invalid role index size" }
            return FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                // This asset is packaged from the checksum-verified distribution. Check
                // its directory at open and the bounded block records as they are read.
                read(channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, length), validateAllRecords = false)
            }
        }

        internal fun read(source: ByteBuffer): RuntimeGrammarRoles = read(source, validateAllRecords = true)

        private fun read(source: ByteBuffer, validateAllRecords: Boolean): RuntimeGrammarRoles {
            val data = source.slice().asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN)
            require(data.capacity() in HEADER_SIZE..MAX_INDEX_SIZE && data.getInt(0) == MAGIC &&
                data.getInt(4) in 1..2 && data.getInt(8) == data.capacity()) {
                "Invalid role index header"
            }
            // Keep the mapping and validated block directory after this bulk check.
            val bytes = ByteArray(data.capacity())
            data.duplicate().get(bytes)
            val crc = CRC32().apply { update(bytes, HEADER_SIZE, bytes.size - HEADER_SIZE) }
            require(crc.value.toInt() == data.getInt(12)) { "Invalid role index checksum" }
            return RuntimeGrammarRoles(data, ByteBuffer.wrap(bytes), validateAllRecords)
        }
    }
}

private fun ByteBuffer.readUnsigned(): Int {
    var result = 0
    for (shift in 0..28 step 7) {
        require(hasRemaining()) { "Truncated key length" }
        val byte = get().toInt() and 255
        require(shift != 28 || byte <= 7) { "Overflowing key length" }
        result = result or ((byte and 127) shl shift)
        if (byte < 128) {
            require(shift == 0 || byte != 0) { "Noncanonical key length" }
            return result
        }
    }
    error("Invalid key length")
}

// Keep the unsigned comparison available on Android API 24 as well.
private fun compareBytes(left: ByteArray, leftLength: Int, right: ByteArray, rightLength: Int): Int {
    for (i in 0 until minOf(leftLength, rightLength)) {
        val order = (left[i].toInt() and 255) - (right[i].toInt() and 255)
        if (order != 0) return order
    }
    return leftLength - rightLength
}

internal fun interface GrammarRoles {
    fun lookup(surface: String, normalized: String): Set<String>
}

internal fun decodeRoles(bits: LongArray, categoryNames: List<String>): Set<String> {
    if (bits.all { it == 0L }) return emptySet()
    return buildSet {
        for (word in bits.indices) {
            var remaining = bits[word]
            while (remaining != 0L) {
                add(categoryNames[word * 64 + remaining.countTrailingZeroBits()])
                remaining = remaining and (remaining - 1)
            }
        }
    }
}

internal fun compareKey(data: ByteBuffer, key: ByteArray, start: Int, length: Int): Int {
    for (i in 0 until minOf(key.size, length)) {
        val order = (key[i].toInt() and 255) - (data.get(start + i).toInt() and 255)
        if (order != 0) return order
    }
    return key.size - length
}
