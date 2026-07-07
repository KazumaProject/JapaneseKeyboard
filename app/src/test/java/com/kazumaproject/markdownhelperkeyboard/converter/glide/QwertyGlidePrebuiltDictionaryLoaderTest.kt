package com.kazumaproject.markdownhelperkeyboard.converter.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.DataOutputStream

class QwertyGlidePrebuiltDictionaryLoaderTest {
    @Test
    fun bundledEnglishDictionaryCanLoadPrebuiltIndex() {
        val loader = loader(
            isBundledEnglishDictionaryActive = true,
            openBundledIndex = { validIndex("an").inputStream() }
        )

        val result = loader.load()

        assertTrue(result is QwertyGlidePrebuiltLoadResult.Loaded)
        assertEquals(1, (result as QwertyGlidePrebuiltLoadResult.Loaded).provider.entryCount)
    }

    @Test
    fun externalEnglishDictionarySkipsBundledPrebuiltIndex() {
        var opened = false
        val loader = loader(
            isBundledEnglishDictionaryActive = false,
            openBundledIndex = {
                opened = true
                validIndex("an").inputStream()
            }
        )

        val result = loader.load()

        assertTrue(result is QwertyGlidePrebuiltLoadResult.NotAvailable)
        assertTrue(!opened)
    }

    @Test
    fun invalidBundledIndexReturnsInvalidResult() {
        val loader = loader(
            isBundledEnglishDictionaryActive = true,
            openBundledIndex = { byteArrayOf(0, 1, 2).inputStream() }
        )

        assertTrue(loader.load() is QwertyGlidePrebuiltLoadResult.Invalid)
    }

    @Test
    fun missingBundledIndexReturnsNotAvailableResult() {
        val loader = loader(
            isBundledEnglishDictionaryActive = true,
            openBundledIndex = { throw FileNotFoundException("missing") }
        )

        assertTrue(loader.load() is QwertyGlidePrebuiltLoadResult.NotAvailable)
    }

    @Test
    fun unexpectedExceptionIsConvertedToInvalidResult() {
        val loader = loader(
            isBundledEnglishDictionaryActive = true,
            openBundledIndex = { throw IllegalStateException("boom") }
        )

        assertTrue(loader.load() is QwertyGlidePrebuiltLoadResult.Invalid)
    }

    private fun loader(
        isBundledEnglishDictionaryActive: Boolean,
        openBundledIndex: () -> InputStream,
    ) = QwertyGlidePrebuiltDictionaryLoader(
        openBundledIndex = openBundledIndex,
        isBundledEnglishDictionaryActive = { isBundledEnglishDictionaryActive },
        reader = QwertyGlideIndexBinaryReader(),
    )

    private fun ByteArray.inputStream() = ByteArrayInputStream(this)

    private fun validIndex(word: String): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeUTF(QwertyGlideIndexBinaryReader.MAGIC)
            data.writeInt(QwertyGlideIndexBinaryReader.VERSION)
            data.writeInt(1)
            data.writeUTF(word)
            data.writeInt(10)
            data.writeChar(word.first().code)
            data.writeChar(word.last().code)
            data.writeInt(word.length)
            data.writeInt(word.characterMask())
            data.writeLong(word.transitionMask())
        }
        return output.toByteArray()
    }
}
