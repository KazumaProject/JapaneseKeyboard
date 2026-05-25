package com.kazumaproject.markdownhelperkeyboard.converter.glide

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class QwertyGlideIndexBinaryReaderTest {
    @Test
    fun readsValidVersionOneIndex() {
        val entries = QwertyGlideIndexBinaryReader().read(
            binaryIndex(
                TestEntry("an", 10),
                TestEntry("as", 20),
            ).inputStream()
        )

        assertEquals(listOf("an", "as"), entries.map { it.word })
        assertEquals(10, entries.first().wordCost)
    }

    @Test
    fun failsWhenMagicIsWrong() {
        assertFailsWith<QwertyGlideIndexFormatException> {
            QwertyGlideIndexBinaryReader().read(
                binaryIndex(TestEntry("an"), magic = "BAD").inputStream()
            )
        }
    }

    @Test
    fun failsWhenVersionIsUnsupported() {
        assertFailsWith<QwertyGlideIndexFormatException> {
            QwertyGlideIndexBinaryReader().read(
                binaryIndex(TestEntry("an"), version = 2).inputStream()
            )
        }
    }

    @Test
    fun failsWhenEntryCountIsZero() {
        assertFailsWith<QwertyGlideIndexFormatException> {
            QwertyGlideIndexBinaryReader().read(binaryIndex().inputStream())
        }
    }

    @Test
    fun failsWhenWordContainsNonLowercaseAscii() {
        assertFailsWith<QwertyGlideIndexFormatException> {
            QwertyGlideIndexBinaryReader().read(binaryIndex(TestEntry("a1")).inputStream())
        }
    }

    @Test
    fun failsWhenWordLengthIsOutOfRange() {
        assertFailsWith<QwertyGlideIndexFormatException> {
            QwertyGlideIndexBinaryReader().read(binaryIndex(TestEntry("a")).inputStream())
        }
        assertFailsWith<QwertyGlideIndexFormatException> {
            QwertyGlideIndexBinaryReader().read(binaryIndex(TestEntry("a".repeat(25))).inputStream())
        }
    }

    @Test
    fun failsWhenFirstCharDoesNotMatchWord() {
        assertFailsWith<QwertyGlideIndexFormatException> {
            QwertyGlideIndexBinaryReader().read(
                binaryIndex(TestEntry("an", firstChar = 'b')).inputStream()
            )
        }
    }

    @Test
    fun failsWhenLastCharDoesNotMatchWord() {
        assertFailsWith<QwertyGlideIndexFormatException> {
            QwertyGlideIndexBinaryReader().read(
                binaryIndex(TestEntry("an", lastChar = 'm')).inputStream()
            )
        }
    }

    @Test
    fun failsWhenLengthDoesNotMatchWord() {
        assertFailsWith<QwertyGlideIndexFormatException> {
            QwertyGlideIndexBinaryReader().read(
                binaryIndex(TestEntry("an", length = 3)).inputStream()
            )
        }
    }

    @Test
    fun failsWhenCharacterMaskDoesNotMatchWord() {
        assertFailsWith<QwertyGlideIndexFormatException> {
            QwertyGlideIndexBinaryReader().read(
                binaryIndex(TestEntry("an", characterMask = 0)).inputStream()
            )
        }
    }

    @Test
    fun failsWhenTransitionMaskDoesNotMatchWord() {
        assertFailsWith<QwertyGlideIndexFormatException> {
            QwertyGlideIndexBinaryReader().read(
                binaryIndex(TestEntry("an", transitionMask = 0L)).inputStream()
            )
        }
    }

    @Test
    fun failsWhenSortOrderIsBroken() {
        assertFailsWith<QwertyGlideIndexFormatException> {
            QwertyGlideIndexBinaryReader().read(
                binaryIndex(TestEntry("as"), TestEntry("an")).inputStream()
            )
        }
    }

    @Test
    fun failsWhenTrailingBytesExist() {
        assertFailsWith<QwertyGlideIndexFormatException> {
            QwertyGlideIndexBinaryReader().read(
                binaryIndex(TestEntry("an"), trailingBytes = byteArrayOf(1)).inputStream()
            )
        }
    }

    @Test
    fun failsWhenEntryCountExceedsLimit() {
        assertFailsWith<QwertyGlideIndexFormatException> {
            QwertyGlideIndexBinaryReader(maxEntryCount = 1).read(
                binaryIndex(TestEntry("an"), TestEntry("as")).inputStream()
            )
        }
    }

    private fun ByteArray.inputStream() = ByteArrayInputStream(this)

    private fun binaryIndex(
        vararg entries: TestEntry,
        magic: String = QwertyGlideIndexBinaryReader.MAGIC,
        version: Int = QwertyGlideIndexBinaryReader.VERSION,
        trailingBytes: ByteArray = byteArrayOf(),
    ): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeUTF(magic)
            data.writeInt(version)
            data.writeInt(entries.size)
            entries.forEach { entry ->
                data.writeUTF(entry.word)
                data.writeInt(entry.wordCost)
                data.writeChar((entry.firstChar ?: entry.word.first()).code)
                data.writeChar((entry.lastChar ?: entry.word.last()).code)
                data.writeInt(entry.length ?: entry.word.length)
                data.writeInt(entry.characterMask ?: entry.word.characterMask())
                data.writeLong(entry.transitionMask ?: entry.word.transitionMask())
            }
            data.write(trailingBytes)
        }
        return output.toByteArray()
    }

    private data class TestEntry(
        val word: String,
        val wordCost: Int = 10,
        val firstChar: Char? = null,
        val lastChar: Char? = null,
        val length: Int? = null,
        val characterMask: Int? = null,
        val transitionMask: Long? = null,
    )
}

private inline fun <reified T : Throwable> assertFailsWith(noinline block: () -> Unit) {
    org.junit.Assert.assertThrows(T::class.java, block)
}
