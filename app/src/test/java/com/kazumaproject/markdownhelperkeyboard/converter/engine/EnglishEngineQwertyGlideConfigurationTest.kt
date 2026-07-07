package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlideIndexBinaryReader
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlidePrebuiltDictionaryLoader
import com.kazumaproject.markdownhelperkeyboard.converter.glide.characterMask
import com.kazumaproject.markdownhelperkeyboard.converter.glide.transitionMask
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.FileNotFoundException
import java.io.InputStream

class EnglishEngineQwertyGlideConfigurationTest {
    @Test
    fun enabledPreferenceWithPrebuiltSuccessUsesReadyDecoder() {
        val engine = EnglishEngine()

        engine.configureQwertyGlideDecoder(
            enabled = true,
            canUseBundledPrebuiltIndex = true,
            prebuiltDictionaryLoader = loader { validIndex("an").inputStream() },
        )

        assertTrue(engine.isQwertyGlideInputEnabled())
        assertTrue(engine.isQwertyGlideDictionaryReady())
        assertTrue(engine.hasQwertyGlideDecoder())
        assertFalse(engine.isQwertyGlideWarmupActive())
    }

    @Test
    fun enabledPreferenceWithUnavailablePrebuiltLeavesRuntimeWarmupPathAvailable() {
        val engine = EnglishEngine()

        engine.configureQwertyGlideDecoder(
            enabled = true,
            canUseBundledPrebuiltIndex = true,
            prebuiltDictionaryLoader = loader { throw FileNotFoundException("missing") },
        )

        assertTrue(engine.isQwertyGlideInputEnabled())
        assertFalse(engine.isQwertyGlideDictionaryReady())
        assertFalse(engine.hasQwertyGlideDecoder())
        assertFalse(engine.isQwertyGlideWarmupActive())
    }

    @Test
    fun enabledPreferenceWithExternalOverrideSkipsPrebuiltLoader() {
        var opened = false
        val engine = EnglishEngine()

        engine.configureQwertyGlideDecoder(
            enabled = true,
            canUseBundledPrebuiltIndex = false,
            prebuiltDictionaryLoader = loader {
                opened = true
                validIndex("an").inputStream()
            },
        )

        assertTrue(engine.isQwertyGlideInputEnabled())
        assertFalse(engine.isQwertyGlideDictionaryReady())
        assertFalse(engine.hasQwertyGlideDecoder())
        assertFalse(opened)
    }

    @Test
    fun disabledPreferenceSkipsPrebuiltLoaderAndClearsState() {
        var opened = false
        val engine = EnglishEngine()
        engine.configureQwertyGlideDecoder(
            enabled = true,
            canUseBundledPrebuiltIndex = true,
            prebuiltDictionaryLoader = loader { validIndex("an").inputStream() },
        )

        engine.configureQwertyGlideDecoder(
            enabled = false,
            canUseBundledPrebuiltIndex = true,
            prebuiltDictionaryLoader = loader {
                opened = true
                validIndex("as").inputStream()
            },
        )

        assertFalse(engine.isQwertyGlideInputEnabled())
        assertFalse(engine.isQwertyGlideDictionaryReady())
        assertFalse(engine.hasQwertyGlideDecoder())
        assertFalse(engine.isQwertyGlideWarmupActive())
        assertFalse(opened)
    }

    @Test
    fun offToOnTransitionAttemptsPrebuiltInitialization() {
        var opened = false
        val engine = EnglishEngine()
        engine.configureQwertyGlideDecoder(
            enabled = false,
            canUseBundledPrebuiltIndex = true,
            prebuiltDictionaryLoader = loader { validIndex("an").inputStream() },
        )

        engine.configureQwertyGlideDecoder(
            enabled = true,
            canUseBundledPrebuiltIndex = true,
            prebuiltDictionaryLoader = loader {
                opened = true
                validIndex("an").inputStream()
            },
        )

        assertTrue(opened)
        assertTrue(engine.isQwertyGlideDictionaryReady())
    }

    private fun loader(openBundledIndex: () -> InputStream) = QwertyGlidePrebuiltDictionaryLoader(
        openBundledIndex = openBundledIndex,
        isBundledEnglishDictionaryActive = { true },
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
