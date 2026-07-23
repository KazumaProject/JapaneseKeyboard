package com.kazumaproject.markdownhelperkeyboard.gemma.media

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object GemmaPcmWavWriter {
    fun writeMono16Bit(
        rawFile: File,
        wavFile: File,
        sampleRate: Int,
    ) {
        val rawSize = rawFile.length()
        FileOutputStream(wavFile).use { output ->
            output.write(mono16BitHeader(rawSize, sampleRate))
            FileInputStream(rawFile).use { input -> input.copyTo(output) }
        }
    }

    internal fun mono16BitHeader(dataSize: Long, sampleRate: Int): ByteArray {
        val bytesPerSample = 2
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt((36L + dataSize).toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(sampleRate)
            putInt(sampleRate * bytesPerSample)
            putShort(bytesPerSample.toShort())
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize.toInt())
        }.array()
    }
}
