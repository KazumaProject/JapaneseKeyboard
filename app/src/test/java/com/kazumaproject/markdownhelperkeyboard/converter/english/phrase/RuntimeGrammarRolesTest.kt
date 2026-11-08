package com.kazumaproject.markdownhelperkeyboard.converter.english.phrase

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RuntimeGrammarRolesTest {
    private val names = listOf("noun", "noun_proper") + (2..69).map { "category_$it" }
    private val keys = ((0..100).map { "Long Shared Prefix Person " + it.toString().padStart(4, '0') } +
        listOf("long shared prefix person 0032", "Pokémon", "Pokémons", "東京さま", "東京さん", "😀", "z".repeat(200)))
        .sortedWith { a, b -> compareUnsigned(a.toByteArray(), b.toByteArray()) }
    private val roles = keys.withIndex().associate { (i, key) -> key to setOf(names[i % names.size]) }

    @Test
    fun bothVersionsFindKeysAcrossBlocksAndMergeSurfaceWithNormalizedRoles() {
        for (version in 1..2) {
            val reader = RuntimeGrammarRoles.read(ByteBuffer.wrap(fixture(version)))
            for (key in keys + keys.map { "$it!" } + listOf("", "A", "Long", "ZZZ", "Pokém", "東京")) {
                val normalized = key.lowercase()
                val expected = roles[key].orEmpty() + roles[normalized].orEmpty()
                assertEquals("v$version: $key", expected, reader.lookup(key, normalized))
            }
        }
    }

    @Test
    fun readsOnlyTheRequestedSliceOfADirectBuffer() {
        val bytes = fixture(2)
        val direct = ByteBuffer.allocateDirect(bytes.size + 13)
        direct.position(7)
        direct.put(bytes)
        direct.limit(7 + bytes.size)
        direct.position(7)
        val reader = RuntimeGrammarRoles.read(direct)
        assertEquals(7, direct.position())
        assertEquals(roles.getValue(keys.last()), reader.lookup(keys.last(), keys.last()))
    }

    @Test
    fun rejectsCorruptHeadersAndChecksums() {
        for (version in 1..2) {
            val valid = fixture(version)
            for (offset in listOf(0, 4, 8, 12, valid.lastIndex)) {
                val bytes = valid.copyOf()
                bytes[offset] = (bytes[offset].toInt() xor 0x40).toByte()
                assertThrows(IllegalArgumentException::class.java) { RuntimeGrammarRoles.read(ByteBuffer.wrap(bytes)) }
            }
        }
    }

    @Test
    fun rejectsMalformedBlocksEvenWithAValidChecksum() {
        val valid = fixture(2)
        val header = ByteBuffer.wrap(valid)
        val rows = header.getInt(32)
        val text = header.getInt(36)
        val mutations: List<(ByteBuffer) -> Unit> = listOf(
            { it.putInt(rows, 1) },
            { it.putInt(rows + 4, Int.MAX_VALUE) },
            { it.put(text, 1) }, // A block's first key must be complete.
            { it.put(text + 1, 0) }, // Empty suffix.
            { it.putShort(text + 2, (-1).toShort()) },
            { it.putInt(24, 1) },
            { for (i in 0..4) it.put(text + i, 0xff.toByte()) },
            { it.put(text, 0x80.toByte()); it.put(text + 1, 0) }, // Noncanonical varint.
            { it.put(text + 4, 'Z'.code.toByte()) }, // Break ordering of the first block.
        )
        for ((index, mutate) in mutations.withIndex()) {
            val bytes = valid.copyOf()
            mutate(ByteBuffer.wrap(bytes))
            checksum(bytes)
            assertThrows("mutation $index", IllegalArgumentException::class.java) {
                RuntimeGrammarRoles.read(ByteBuffer.wrap(bytes))
            }
        }
    }

    private fun fixture(version: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val writer = DataOutputStream(output)
        writer.write(ByteArray(40))
        names.forEach { writer.writeShort(it.toByteArray().size); writer.write(it.toByteArray()) }
        val masks = output.size()
        names.indices.forEach { id ->
            repeat(2) { word -> writer.writeLong(if (id / 64 == word) 1L shl (id % 64) else 0L) }
        }
        val rows = output.size()
        val records = ByteArrayOutputStream()
        val recordWriter = DataOutputStream(records)
        if (version == 1) {
            keys.forEachIndexed { i, key ->
                writer.writeInt(records.size())
                writer.writeShort(i % names.size)
                recordWriter.write(key.toByteArray())
            }
        } else {
            keys.chunked(32).forEachIndexed { block, entries ->
                writer.writeInt(records.size())
                var previous = ByteArray(0)
                entries.forEachIndexed { index, key ->
                    val bytes = key.toByteArray()
                    var prefix = 0
                    while (prefix < minOf(previous.size, bytes.size) && previous[prefix] == bytes[prefix]) prefix++
                    recordWriter.writeUnsigned(prefix)
                    recordWriter.writeUnsigned(bytes.size - prefix)
                    recordWriter.writeShort((block * 32 + index) % names.size)
                    recordWriter.write(bytes, prefix, bytes.size - prefix)
                    previous = bytes
                }
            }
        }
        writer.writeInt(records.size())
        val text = output.size()
        records.writeTo(output)
        val bytes = output.toByteArray()
        ByteBuffer.wrap(bytes).apply {
            listOf(0x45524F4C, version, bytes.size, 0, names.size, names.size, keys.size, masks, rows, text)
                .forEach(::putInt)
        }
        checksum(bytes)
        return bytes
    }

    private fun checksum(bytes: ByteArray) {
        ByteBuffer.wrap(bytes).putInt(12, CRC32().apply { update(bytes, 40, bytes.size - 40) }.value.toInt())
    }

    private fun DataOutputStream.writeUnsigned(value: Int) {
        var remaining = value
        while (remaining >= 128) {
            writeByte((remaining and 127) or 128)
            remaining = remaining ushr 7
        }
        writeByte(remaining)
    }

    private fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val difference = (a[i].toInt() and 255) - (b[i].toInt() and 255)
            if (difference != 0) return difference
        }
        return a.size - b.size
    }
}
