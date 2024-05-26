package com.kazumaproject.connection_id

import com.kazumaproject.markdownhelperkeyboard.converter.stream.ArraysStream
import com.kazumaproject.toByteArrayFromListShort
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ObjectInput
import java.io.ObjectOutput
import java.nio.ByteBuffer

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
        inputStream: InputStream
    ): ShortArray {
        val byteArray = inputStream.readBytes()
        val byteBuffer = ByteBuffer.wrap(byteArray)
        val shortArray = ShortArray(byteArray.size / 2)
        byteBuffer.asShortBuffer().get(shortArray)
        return shortArray
    }

}