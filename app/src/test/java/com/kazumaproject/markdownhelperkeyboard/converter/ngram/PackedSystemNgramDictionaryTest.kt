package com.kazumaproject.markdownhelperkeyboard.converter.ngram

import com.kazumaproject.graph.Node
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

class PackedSystemNgramDictionaryTest {
    private val fixture: ByteArray
        get() = checkNotNull(javaClass.classLoader?.getResourceAsStream("ngram/system_ngram_v3_test.dat"))
            .use { it.readBytes() }

    @Test
    fun exactRuleMatchesAndOneCharacterDifferenceDoesNot() {
        val dictionary = dictionary()
        assertTrue(dictionary.matches(node("服"), node("を"), node("着る"), null, null))
        assertFalse(dictionary.matches(node("服"), node("を"), node("切る"), null, null))
    }

    @Test
    fun firstPairIndexHasNoFalseNegativeAndRejectsMissingPrefix() {
        val dictionary = dictionary()
        assertTrue(dictionary.mayMatchFirstPair(node("服"), node("を")))
        assertTrue(dictionary.mayMatchFirstPair(node("一"), node("二")))
        assertFalse(dictionary.mayMatchFirstPair(node("存在しない前半"), node("存在しない後半")))
        assertTrue(dictionary.mayMatchFirstNode(node("服")))
        assertFalse(dictionary.mayMatchFirstNode(node("存在しない開始語")))
    }

    @Test
    fun firstPairWordHashMatchesSupplementaryCharactersWithoutAllocatingQueryBytes() {
        val method = PackedSystemNgramDictionary::class.java.getDeclaredMethod(
            "utf8StringHash",
            ByteArray::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        val dictionary = dictionary()
        listOf("日本語", "ASCII", "𠮷野家", "😀候補").forEach { value ->
            val encoded = value.toByteArray(Charsets.UTF_8)
            assertTrue(
                "hash mismatch for $value",
                method.invoke(dictionary, encoded, 0, encoded.size) == value.hashCode(),
            )
        }
    }

    @Test
    fun coarsePosRuleMatchesOnlyTheRequestedPos() {
        val dictionary = dictionary()
        assertTrue(
            dictionary.matches(node("布"), node("で"), node("机", NOUN_CONTEXT_ID), node("を"), node("拭く")),
        )
        assertFalse(
            dictionary.matches(node("布"), node("で"), node("動く", VERB_CONTEXT_ID), node("を"), node("拭く")),
        )
    }

    @Test
    fun wildcardConsumesExactlyOneNode() {
        val dictionary = dictionary()
        assertTrue(dictionary.matches(node("布"), node("で"), node("雑巾"), node("を"), node("洗う")))
        assertFalse(dictionary.matches(node("布"), node("で"), node("を"), node("洗う"), null))
        assertFalse(dictionary.matches(node("布"), node("で"), node("古い"), node("雑巾"), node("を")))
    }

    @Test
    fun readsRulesFromTwoThroughFiveGrams() {
        val dictionary = dictionary()
        assertTrue(dictionary.matches(node("二"), node("語"), null, null, null))
        assertTrue(dictionary.matches(node("服"), node("を"), node("着る"), null, null))
        assertTrue(dictionary.matches(node("一"), node("二"), node("三"), node("四"), null))
        assertTrue(dictionary.matches(node("一"), node("二"), node("三"), node("四"), node("五")))
    }

    @Test
    fun rejectsBadMagicVersionCrcOffsetsAndTruncation() {
        assertRejected(fixture.copyOf().also { it[0] = 0 })
        assertRejected(fixture.copyOf().also { littleEndian(it).putInt(4, 99) })
        assertRejected(fixture.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() })
        assertRejected(fixture.copyOf().also {
            littleEndian(it).putInt(40, littleEndian(it).getInt(40) + 4)
            updateCrc(it)
        })
        assertRejected(fixture.copyOf(fixture.size - 1))
    }

    @Test
    fun hashIndexPointingAtAnotherRecordCannotCreateFalseMatch() {
        val bytes = fixture.copyOf()
        val buffer = littleEndian(bytes)
        val key = canonicalExactKey("服", "を", "着る")
        val hash = hash64(key)
        val bucketCount = buffer.getInt(64)
        val bucketBits = Integer.numberOfTrailingZeros(bucketCount)
        val bucket = (hash ushr (64 - bucketBits)).toInt() and (bucketCount - 1)
        val bucketOffsets = buffer.getInt(40)
        val entries = buffer.getInt(44)
        val start = buffer.getInt(bucketOffsets + bucket * 4)
        val end = buffer.getInt(bucketOffsets + (bucket + 1) * 4)
        val wanted = hash and 0x0000ffffffffffffL
        var changed = false
        for (entry in start until end) {
            val offset = entries + entry * 10
            if (readUInt48(bytes, offset) == wanted) {
                buffer.putInt(offset + 6, (buffer.getInt(offset + 6) + 1) % buffer.getInt(8))
                changed = true
                break
            }
        }
        assertTrue("fixture must contain the target hash", changed)
        updateCrc(bytes)
        val dictionary = PackedSystemNgramDictionary.read(bytes)
        assertFalse(dictionary.matches(node("服"), node("を"), node("着る"), null, null))
    }

    @Test
    fun readerOwnsTheOriginalDictionaryByteArrayWithoutCopying() {
        val bytes = fixture
        val dictionary = PackedSystemNgramDictionary.read(bytes)
        val field = PackedSystemNgramDictionary::class.java.getDeclaredField("bytes").apply {
            isAccessible = true
        }
        assertSame(bytes, field.get(dictionary))
    }

    private fun dictionary() = PackedSystemNgramDictionary.read(fixture)

    private fun assertRejected(bytes: ByteArray) {
        try {
            PackedSystemNgramDictionary.read(bytes)
            fail("corrupt dictionary was accepted")
        } catch (_: RuntimeException) {
            // Any deterministic parse/validation failure is an acceptable rejection.
        }
    }

    private fun canonicalExactKey(vararg words: String): ByteArray {
        var signature = words.size
        repeat(words.size) { signature = signature or (1 shl (3 + it * 2)) }
        val encoded = words.map { it.toByteArray(Charsets.UTF_8) }
        val result = ByteArray(2 + encoded.sumOf { 1 + it.size })
        var position = 0
        result[position++] = signature.toByte()
        result[position++] = (signature ushr 8).toByte()
        encoded.forEach {
            check(it.size < 128)
            result[position++] = it.size.toByte()
            it.copyInto(result, position)
            position += it.size
        }
        return result
    }

    private fun hash64(bytes: ByteArray): Long {
        var hash = -3750763034362895579L
        bytes.forEach { hash = (hash xor (it.toLong() and 0xffL)) * 1099511628211L }
        hash = (hash xor (hash ushr 33)) * -49064778989728563L
        hash = (hash xor (hash ushr 33)) * -4265267296055464877L
        return hash xor (hash ushr 33)
    }

    private fun readUInt48(bytes: ByteArray, offset: Int): Long {
        var value = 0L
        repeat(6) { value = value or ((bytes[offset + it].toLong() and 0xffL) shl (it * 8)) }
        return value
    }

    private fun updateCrc(bytes: ByteArray) {
        val crc = CRC32().apply { update(bytes, HEADER_SIZE, bytes.size - HEADER_SIZE) }
        littleEndian(bytes).putInt(60, crc.value.toInt())
    }

    private fun littleEndian(bytes: ByteArray): ByteBuffer =
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    private fun node(word: String, leftId: Int = 1) = Node(
        l = leftId.toShort(),
        r = leftId.toShort(),
        score = 0,
        f = 0,
        tango = word,
        len = 1,
        yomiUsed = word,
        sPos = 0,
    )

    private companion object {
        const val HEADER_SIZE = 80
        const val NOUN_CONTEXT_ID = 1851
        const val VERB_CONTEXT_ID = 434
    }
}
