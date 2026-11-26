package com.kazumaproject.markdownhelperkeyboard.dictionary_override

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.luben.zstd.ZstdOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZstandardDictionaryInstrumentedTest {
    @Test
    fun verifiesAndCachesZstandardAssetsWithTheExistingContentAddress() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File.createTempFile("zstandard-test-", null, context.cacheDir)
        directory.delete()
        check(directory.mkdir())
        try {
            val bytes = "English dictionary café".repeat(4096).encodeToByteArray()
            val checksum = MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()
            val path = "english/reading.compact"
            val size = bytes.size
            val manifest = """{"version":1,"assets":{"$path":{
                "size":$size,"sha256":"$checksum","compression":"zstd"}}}"""
            val compressed = ByteArrayOutputStream().also { buffer ->
                ZstdOutputStream(buffer).setWindowLog(22).use { it.write(bytes) }
            }.toByteArray()
            var opens = 0
            var damaged = true
            val cache = MappedDictionaryAssetCache(directory, manifest) { name ->
                assertEquals("$path.zst", name)
                opens++
                ByteArrayInputStream(if (damaged) compressed.copyOf(compressed.size / 2) else compressed)
            }
            assertThrows(IOException::class.java) { cache.fileFor(path) }
            assertEquals(listOf(".lock"), directory.listFiles()!!.map { it.name })
            damaged = false
            assertArrayEquals(bytes, cache.readBytes(path))
            assertEquals(checksum, cache.fileFor(path).name)
            assertArrayEquals(bytes, cache.fileFor(path).readBytes())
            assertEquals(3, opens)
        } finally {
            directory.deleteRecursively()
        }
    }
}
