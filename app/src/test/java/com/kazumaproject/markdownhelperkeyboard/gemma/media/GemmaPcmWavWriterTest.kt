package com.kazumaproject.markdownhelperkeyboard.gemma.media

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class GemmaPcmWavWriterTest {
    @Test
    fun mono16BitHeader_containsExpectedPcmMetadata() {
        val header = GemmaPcmWavWriter.mono16BitHeader(
            dataSize = 32_000L,
            sampleRate = 16_000,
        )
        val littleEndian = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(44, header.size)
        assertArrayEquals("RIFF".toByteArray(), header.copyOfRange(0, 4))
        assertEquals(32_036, littleEndian.getInt(4))
        assertArrayEquals("WAVE".toByteArray(), header.copyOfRange(8, 12))
        assertEquals(1, littleEndian.getShort(20).toInt())
        assertEquals(1, littleEndian.getShort(22).toInt())
        assertEquals(16_000, littleEndian.getInt(24))
        assertEquals(32_000, littleEndian.getInt(28))
        assertEquals(16, littleEndian.getShort(34).toInt())
        assertArrayEquals("data".toByteArray(), header.copyOfRange(36, 40))
        assertEquals(32_000, littleEndian.getInt(40))
    }
}
