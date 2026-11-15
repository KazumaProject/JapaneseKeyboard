package com.kazumaproject.markdownhelperkeyboard.dictionary_override

import com.google.gson.JsonObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream

class MappedDictionaryAssetCacheTest {
    @get:Rule val temp = TemporaryFolder()
    private val path = "english/reading.compact"
    private val bytes = "Dictionary \u0000 \uffff 日本語".repeat(2048).toByteArray()

    @Test fun extractsVerifiedBytesOnceAndReusesReadOnlyFile() {
        val opens = AtomicInteger()
        val cache = cache(temp.newFolder(), bytes, opens)
        val file = cache.fileFor(path)
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(hash(bytes), file.name)
        assertEquals(file, cache.fileFor(path))
        assertEquals(1, opens.get())
    }

    @Test fun changedDictionaryReplacesOldCopyWithoutKeepingOldVersions() {
        val directory = temp.newFolder()
        val old = cache(directory, bytes).fileFor(path)
        val updated = bytes + byteArrayOf(1)
        val file = cache(directory, updated).fileFor(path)
        assertFalse(old.exists())
        assertArrayEquals(updated, file.readBytes())
        assertEquals(2, directory.listFiles()!!.size) // Current copy and process lock.
    }

    @Test fun truncatedExistingCopyIsRebuilt() {
        val cache = cache(temp.newFolder(), bytes)
        val file = cache.fileFor(path)
        assertTrue(file.setWritable(true))
        file.writeBytes(byteArrayOf(0))
        assertArrayEquals(bytes, cache.fileFor(path).readBytes())
    }

    @Test fun damagedStreamLeavesNoPartialDictionaryAndCanBeRetried() {
        val directory = temp.newFolder()
        val compressed = compress(bytes)
        var damaged = true
        val cache = MappedDictionaryAssetCache(directory, manifest(bytes)) {
            ByteArrayInputStream(if (damaged) compressed.copyOf(compressed.size - 8) else compressed)
        }
        assertThrows(IOException::class.java) { cache.fileFor(path) }
        assertEquals(listOf(".lock"), directory.listFiles()!!.map { it.name })
        damaged = false
        assertArrayEquals(bytes, cache.fileFor(path).readBytes())
    }

    @Test fun wrongChecksumAndOversizedOutputAreRejected() {
        for (metadata in listOf(manifest(bytes, digest = "0".repeat(64)), manifest(bytes, size = bytes.size - 1))) {
            val directory = temp.newFolder()
            val cache = MappedDictionaryAssetCache(directory, metadata) { ByteArrayInputStream(compress(bytes)) }
            assertThrows(IOException::class.java) { cache.fileFor(path) }
            assertThrows(IOException::class.java) { cache.readBytes(path) }
            assertFalse(File(directory, hash(bytes)).exists())
        }
    }

    @Test fun memoryFallbackWorksWhenCacheDirectoryCannotBeWritten() {
        val cache = cache(temp.newFile(), bytes)
        assertThrows(IOException::class.java) { cache.fileFor(path) }
        assertArrayEquals(bytes, cache.readBytes(path))
    }

    @Test fun concurrentReadersAndCacheInstancesPublishOnlyOneCopy() {
        val directory = temp.newFolder()
        val opens = AtomicInteger()
        val caches = List(4) { cache(directory, bytes, opens) }
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results = caches.map { cache -> executor.submit<File> { cache.fileFor(path) } }
                .map { it.get(30, TimeUnit.SECONDS) }
            assertEquals(1, results.toSet().size)
            assertEquals(1, opens.get())
            assertArrayEquals(bytes, results.first().readBytes())
        } finally {
            executor.shutdownNow()
        }
    }

    private fun cache(directory: File, data: ByteArray, opens: AtomicInteger = AtomicInteger()): MappedDictionaryAssetCache {
        val compressed = compress(data)
        return MappedDictionaryAssetCache(directory, manifest(data)) { name ->
            assertEquals("$path.xz", name)
            opens.incrementAndGet()
            ByteArrayInputStream(compressed)
        }
    }

    private fun manifest(data: ByteArray, digest: String = hash(data), size: Int = data.size): String =
        JsonObject().apply {
            addProperty("version", 1)
            add("assets", JsonObject().apply {
                add(path, JsonObject().apply {
                    addProperty("size", size)
                    addProperty("sha256", digest)
                })
            })
        }.toString()

    private fun hash(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data)
        .joinToString("") { "%02x".format(it) }

    private fun compress(data: ByteArray): ByteArray = ByteArrayOutputStream().also { bytes ->
        XZOutputStream(bytes, LZMA2Options(1)).use { it.write(data) }
    }.toByteArray()
}
