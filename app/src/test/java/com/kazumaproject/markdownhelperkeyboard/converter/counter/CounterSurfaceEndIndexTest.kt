package com.kazumaproject.markdownhelperkeyboard.converter.counter

import com.kazumaproject.counter.CounterConverter
import com.kazumaproject.counter.CounterDictionary
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.CRC32

class CounterSurfaceEndIndexTest {
    private fun bytes() = listOf(File("src/main/assets/counter/counter_rules.dat"), File("app/src/main/assets/counter/counter_rules.dat"))
        .first { it.exists() }.readBytes()

    private fun repairHeader(bytes: ByteArray): ByteArray = bytes.also {
        ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(8, it.size - 16)
            .putInt(12, CRC32().apply { update(it, 16, it.size - 16) }.value.toInt())
    }

    private fun legacy(bytes: ByteArray) = repairHeader(bytes.copyOf(bytes.size - 8196))

    @Test fun publishedIndexMatchesEveryUtf16EndingAndRejectsOrdinaryTails() {
        val dictionary = CounterDictionary.read(bytes())
        val converter = dictionary.converter()
        val expected = buildSet {
            dictionary.units.mapNotNullTo(this) { it.surface.lastOrNull() }
            dictionary.surfaces.mapNotNullTo(this) { it.surface.lastOrNull() }
            dictionary.exceptions.mapNotNullTo(this) { it.suffix.lastOrNull() }
            addAll("0123456789０１２３４５６７８９〇零一二三四五六七八九十百千万億兆京半".toList())
        }
        for (code in 0..65535) {
            assertEquals("U+${code.toString(16)}", code.toChar() in expected, converter.mayEndQuantitySurface("x${code.toChar()}"))
        }
        for (surface in listOf("123本", "二粒", "午後3時半", "12:30", "日本", "本")) assertTrue(surface, converter.mayEndQuantitySurface(surface))
        for (surface in listOf("買う", "出会う", "")) assertFalse(surface, converter.mayEndQuantitySurface(surface))
    }

    @Test fun legacyDictionaryKeepsItsPayloadAndConservativeFallback() {
        val legacy = legacy(bytes())
        assertEquals("7decaa2574df37bd341898f08a655afed0a7987f956ce7fef3bfa5a42f732e08",
            MessageDigest.getInstance("SHA-256").digest(legacy).joinToString("") { "%02x".format(it) })
        val old = CounterDictionary.read(legacy).converter()
        assertFalse(old.mayEndQuantitySurface(""))
        for (surface in listOf("買う", "出会う", "123本", "\ud800", "\uffff")) assertTrue(surface, old.mayEndQuantitySurface(surface))
        val current = CounterDictionary.read(bytes()).converter()
        for (input in listOf("ひゃくにじゅうさんぼん", "なのか", "いつかであう", "ほんとうなのか", "とお", "ごごさんじはん", "２つぶ", "9223372036854775807ほん")) {
            assertEquals(input, old.convert(input), current.convert(input))
            assertEquals(input, old.analyze(input), current.analyze(input))
        }
    }

    @Test fun malformedIndexIsRejectedEvenWithRecomputedChecksum() {
        val published = bytes()
        val offset = published.size - 8196
        for (count in listOf(0, 1023, 1025, -1, Int.MAX_VALUE)) {
            val altered = published.clone().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, count) }
            assertThrows(IllegalArgumentException::class.java) { CounterDictionary.read(repairHeader(altered)) }
        }
        for (length in listOf(offset + 1, offset + 4, published.size - 8, published.size - 1, published.size + 1, published.size + 8)) {
            assertThrows(IllegalArgumentException::class.java) { CounterDictionary.read(repairHeader(published.copyOf(length))) }
        }
        for (version in listOf(0, 2, 99)) {
            val altered = published.clone().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(4, version) }
            assertThrows(IllegalArgumentException::class.java) { CounterDictionary.read(altered) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            CounterDictionary.read(published.clone().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
        }
    }

    @Test fun bitBoundariesAndSurrogatesUseTheLastUtf16Char() {
        val modified = bytes()
        val data = ByteBuffer.wrap(modified).order(ByteOrder.LITTLE_ENDIAN)
        val start = modified.size - 8192
        val codes = listOf(0, 63, 64, 127, 128, 0xd800, 0xdc00, 65535)
        for (code in codes) {
            val offset = start + (code ushr 6) * 8
            data.putLong(offset, data.getLong(offset) or (1L shl (code and 63)))
        }
        val converter = CounterDictionary.read(repairHeader(modified)).converter()
        codes.forEach { assertTrue(converter.mayEndQuantitySurface("x${it.toChar()}")) }
        assertTrue(converter.mayEndQuantitySurface("\ud800\udc00"))
        assertFalse(converter.mayEndQuantitySurface("x\udc01"))
    }

    @Test fun indexIsOwnedByDictionaryAndSharedAcrossConvertersWithoutCopying() {
        val input = bytes()
        val dictionary = input.inputStream().use { CounterDictionary.read(it) }
        val field = CounterDictionary::class.java.declaredFields.single { it.type == LongArray::class.java }
        assertTrue(Modifier.isPrivate(field.modifiers))
        field.isAccessible = true
        val owned = field.get(dictionary)
        assertEquals(1024, (owned as LongArray).size)
        val converters = List(10) { dictionary.converter() }
        val reference = CounterConverter::class.java.declaredFields.single { it.type == CounterDictionary::class.java }.apply { isAccessible = true }
        converters.forEach { assertSame(dictionary, reference.get(it)) }
        assertTrue(CounterConverter::class.java.declaredFields.none { it.type == LongArray::class.java && !Modifier.isStatic(it.modifiers) })
        assertTrue(CounterDictionary::class.java.methods.none { it.returnType == LongArray::class.java })
        assertSame(owned, field.get(dictionary))
        input.fill(0)
        converters.forEach { assertTrue(it.mayEndQuantitySurface("123本")); assertFalse(it.mayEndQuantitySurface("買う")) }
    }
}
