package com.kazumaproject.markdownhelperkeyboard.converter.compact

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BundledEnglishDictionaryReaderTest {
    @Test
    fun readingRestoresLabelsAndTermIds() {
        val labels = charArrayOf(' ', 'a', '\u0000', '\uffff', '\ud800')
        val source = fixture(0x4B455232, labels) {
            writeInt(2)
            writeInt(7)
            writeInt(42)
        }
        val reading = BundledEnglishDictionaryReader.readReading(source)
        assertArrayEquals(labels, reading.labels)
        assertArrayEquals(intArrayOf(7, 42), reading.termIdsSaved)
        assertEquals(0, source.position())
    }

    @Test
    fun wordSupportsAll256UnsignedAlphabetIndices() {
        val labels = CharArray(256) { it.toChar() }.reversedArray()
        val word = BundledEnglishDictionaryReader.readWord(fixture(0x4B455732, labels))
        assertArrayEquals(labels, word.labels)
    }

    @Test
    fun largerAlphabetsAndEmptyLabelsUseRawCharacters() {
        for (labels in listOf(CharArray(257) { it.toChar() }, charArrayOf())) {
            assertArrayEquals(labels,
                BundledEnglishDictionaryReader.readWord(fixture(0x4B455732, labels)).labels)
        }
    }

    @Test
    fun rejectsAnIndexOutsideTheAlphabet() {
        val source = fixture(0x4B455732, charArrayOf('a'))
        source.put(source.limit() - 1, 1.toByte())
        assertThrows(IllegalArgumentException::class.java) {
            BundledEnglishDictionaryReader.readWord(source)
        }
    }

    @Test
    fun rejectsTruncatedLabelsAndTrailingBytes() {
        val source = fixture(0x4B455732, charArrayOf('a', 'b'))
        assertThrows(IllegalArgumentException::class.java) {
            BundledEnglishDictionaryReader.readWord(source.duplicate().apply { limit(limit() - 1) })
        }
        val trailing = ByteBuffer.wrap(source.array() + byteArrayOf(0))
        assertThrows(IllegalArgumentException::class.java) {
            BundledEnglishDictionaryReader.readWord(trailing)
        }
    }

    private fun fixture(
        magic: Int,
        labels: CharArray,
        tail: DataOutputStream.() -> Unit = {},
    ): ByteBuffer {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(magic)
            repeat(2) { out.writeInt(0) } // Empty LBS and leaf bitsets.
            val alphabet = labels.distinct().sorted()
            if (alphabet.isEmpty() || alphabet.size > 256) {
                out.writeInt(0)
                out.writeInt(labels.size)
                labels.forEach { out.writeChar(it.code) }
            } else {
                out.writeInt(alphabet.size)
                alphabet.forEach { out.writeChar(it.code) }
                out.writeInt(labels.size)
                labels.forEach { out.writeByte(alphabet.indexOf(it)) }
            }
            out.tail()
        }
        return ByteBuffer.wrap(bytes.toByteArray())
    }
}
