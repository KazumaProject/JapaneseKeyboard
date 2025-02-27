package com.kazumaproject.markdownhelperkeyboard.converter

import java.io.InputStream
import java.nio.ByteBuffer

class ByteBufferBackedInputStream(private val buffer: ByteBuffer) : InputStream() {
    override fun read(): Int {
        return if (buffer.hasRemaining()) {
            buffer.get().toInt() and 0xFF
        } else {
            -1
        }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (!buffer.hasRemaining()) {
            return -1
        }
        val realLen = minOf(len, buffer.remaining())
        buffer.get(b, off, realLen)
        return realLen
    }
}
