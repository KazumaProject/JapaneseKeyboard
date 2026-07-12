package com.kazumaproject.connection_id

import com.kazumaproject.markdownhelperkeyboard.converter.stream.ArraysStream
import com.kazumaproject.toByteArrayFromListShort
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ObjectInput
import java.io.ObjectOutput

class ConnectionIdBuilder {
    fun build(
        out: ObjectOutput,
        value: List<Short>
    ){
        try {
            out.apply {
                writeObject(value.toByteArrayFromListShort().deflate())
                flush()
                close()
            }
        }catch (e: Exception){
            println(e.message)
        }
    }

    fun read(objectInput: ObjectInput):ShortArray{
        var a: ShortArray
        return try {
            objectInput.apply {
                a = (readObject() as ShortArray)
                close()
            }
            a
        }catch (e: Exception){
            println(e.message)
            shortArrayOf()
        }
    }


    fun buildWithShortArray(
        fileOutputStream: FileOutputStream,
        value: List<Short>,
    ){
        ArraysStream.writeShortArray(
            fileOutputStream,
            value.toShortArray()
        )
    }

     fun readWithShortArray(
        inputStream: InputStream
    ): ShortArray{
        return ArraysStream.readShortArray(inputStream)
    }

    fun readShortArrayFromBytes(
        inputStream: InputStream,
        expectedByteSize: Long? = null,
    ): ShortArray {
        expectedByteSize?.let { byteSize ->
            require(byteSize >= 0) { "connectionId.dat byte size must be non-negative: $byteSize" }
            require(byteSize % Short.SIZE_BYTES == 0L) {
                "connectionId.dat byte size $byteSize is not divisible by ${Short.SIZE_BYTES}"
            }
            val entryCount = byteSize / Short.SIZE_BYTES
            require(entryCount <= Int.MAX_VALUE) {
                "connectionId.dat entry count $entryCount exceeds ${Int.MAX_VALUE}"
            }
            val values = ShortArray(entryCount.toInt())
            val actualEntryCount = readShorts(inputStream, values)
            require(actualEntryCount == values.size) {
                "connectionId.dat ended after $actualEntryCount entries; expected ${values.size}"
            }
            return values
        }

        val builder = ShortArrayBuilder()
        readShorts(inputStream) { value -> builder.add(value) }
        return builder.toShortArray()
    }

    private fun readShorts(inputStream: InputStream, values: ShortArray): Int {
        var index = 0
        readShorts(inputStream) { value ->
            require(index < values.size) {
                "connectionId.dat contains more than ${values.size} entries"
            }
            values[index] = value
            index++
        }
        return index
    }

    private fun readShorts(inputStream: InputStream, onValue: (Short) -> Unit) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var pendingHighByte = -1
        var byteCount = 0L

        while (true) {
            val read = inputStream.read(buffer)
            if (read == -1) break
            byteCount += read.toLong()
            for (i in 0 until read) {
                val byte = buffer[i].toInt() and 0xFF
                if (pendingHighByte == -1) {
                    pendingHighByte = byte
                } else {
                    onValue(((pendingHighByte shl 8) or byte).toShort())
                    pendingHighByte = -1
                }
            }
        }

        require(pendingHighByte == -1) {
            "connectionId.dat byte size $byteCount is not divisible by ${Short.SIZE_BYTES}"
        }
    }

    private class ShortArrayBuilder(
        initialCapacity: Int = 8192,
    ) {
        private var values = ShortArray(initialCapacity)
        private var size = 0

        fun add(value: Short) {
            if (size == values.size) {
                val nextCapacity = if (values.isEmpty()) 1 else values.size * 2
                values = values.copyOf(nextCapacity)
            }
            values[size] = value
            size++
        }

        fun toShortArray(): ShortArray =
            if (size == values.size) values else values.copyOf(size)
    }

}
