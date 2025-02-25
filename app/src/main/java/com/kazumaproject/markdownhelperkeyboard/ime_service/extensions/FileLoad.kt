package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

fun writeIntArrayWithMmap(file: File, data: IntArray) {
    // Size in bytes = number of ints * 4
    val fileSize = data.size.toLong() * Int.SIZE_BYTES

    // Open file in "rw" (read-write) mode
    RandomAccessFile(file, "rw").use { raf ->
        // Ensure file is exactly large enough
        raf.setLength(fileSize)

        // Get the FileChannel
        val channel = raf.channel

        // Map the file into memory
        val mappedBuffer: MappedByteBuffer = channel.map(
            FileChannel.MapMode.READ_WRITE,
            0,
            fileSize
        )

        // Optionally set byte order
        mappedBuffer.order(ByteOrder.nativeOrder())

        // Get an IntBuffer view
        val intBuffer: IntBuffer = mappedBuffer.asIntBuffer()

        // Write the entire IntArray
        intBuffer.put(data)

        // channel & raf will be closed automatically by 'use'
    }
}

fun readIntArrayAsBuffer(file: File): IntBuffer {
    // Check file size
    val fileSize = file.length()
    require(fileSize % Int.SIZE_BYTES == 0L) {
        "File size ($fileSize) not multiple of 4"
    }

    // Use RandomAccessFile in read-only ("r") mode
    val raf = RandomAccessFile(file, "r")
    val channel = raf.channel

    val mappedBuffer: MappedByteBuffer = channel.map(
        FileChannel.MapMode.READ_ONLY,
        0,
        fileSize
    )
    mappedBuffer.order(ByteOrder.nativeOrder())

    // Convert to IntBuffer
    val intBuffer: IntBuffer = mappedBuffer.asIntBuffer()

    // The channel/RAF can be closed now if you want, because
    // the MappedByteBuffer remains valid as long as it is in scope.
    channel.close()
    raf.close()

    return intBuffer
}
