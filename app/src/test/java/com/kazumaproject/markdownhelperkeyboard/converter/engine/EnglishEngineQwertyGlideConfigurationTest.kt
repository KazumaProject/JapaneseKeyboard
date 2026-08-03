package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlideIndexBinaryReader
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlidePrebuiltDictionaryLoader
import com.kazumaproject.markdownhelperkeyboard.converter.glide.characterMask
import com.kazumaproject.markdownhelperkeyboard.converter.glide.transitionMask
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    @Test
    fun asyncConfigurationLoadsPrebuiltWithoutBlockingCaller() = runBlocking {
        val engine = EnglishEngine()
        val loadStarted = CountDownLatch(1)
        val allowLoadToFinish = CountDownLatch(1)

        engine.configureQwertyGlideDecoderAsync(
            enabled = true,
            canUseBundledPrebuiltIndex = true,
            prebuiltDictionaryLoader = loader {
                loadStarted.countDown()
                check(allowLoadToFinish.await(2, TimeUnit.SECONDS))
                validIndex("an").inputStream()
            },
        )

        assertTrue(engine.isQwertyGlideInputEnabled())
        assertTrue(loadStarted.await(2, TimeUnit.SECONDS))
        assertFalse(engine.isQwertyGlideDictionaryReady())
        allowLoadToFinish.countDown()
        engine.awaitQwertyGlideWarmup()
        assertTrue(engine.isQwertyGlideDictionaryReady())
        assertTrue(engine.hasQwertyGlideDecoder())
    }

    @Test
    fun repeatedAsyncConfigurationSharesInFlightLoad() = runBlocking {
        var opens = 0
        val engine = EnglishEngine()
        val loadStarted = CountDownLatch(1)
        val allowLoadToFinish = CountDownLatch(1)
        val prebuiltLoader = loader {
            opens += 1
            loadStarted.countDown()
            check(allowLoadToFinish.await(2, TimeUnit.SECONDS))
            validIndex("an").inputStream()
        }

        engine.configureQwertyGlideDecoderAsync(
            enabled = true,
            canUseBundledPrebuiltIndex = true,
            prebuiltDictionaryLoader = prebuiltLoader,
        )
        assertTrue(loadStarted.await(2, TimeUnit.SECONDS))
        engine.configureQwertyGlideDecoderAsync(
            enabled = true,
            canUseBundledPrebuiltIndex = true,
            prebuiltDictionaryLoader = prebuiltLoader,
        )

        allowLoadToFinish.countDown()
        engine.awaitQwertyGlideWarmup()
        assertTrue(engine.isQwertyGlideDictionaryReady())
        assertTrue(opens == 1)
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
