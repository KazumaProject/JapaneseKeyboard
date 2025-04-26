package com.kazumaproject.markdownhelperkeyboard.converter.stream

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

object ArraysStream {
    fun writeShortArray(
        fileOutputStream: FileOutputStream,
        shortArray: ShortArray
    ) {
        DataOutputStream(BufferedOutputStream(fileOutputStream)).use { bos ->
            bos.writeInt(shortArray.size)
            shortArray.forEach {
                bos.writeShort(it.toInt())
            }
        }
    }

    fun writeCharArray(
        fileOutputStream: FileOutputStream,
        charArray: CharArray
    ) {
        BufferedOutputStream(fileOutputStream).use { bos ->
            bos.write(charArray.size)
            charArray.forEach { bos.write(it.code) }
        }
    }

    fun writeIntArray(
        fileOutputStream: FileOutputStream,
        intArray: IntArray
    ) {
        BufferedOutputStream(fileOutputStream).use { bos ->
            bos.write(intArray.size)
            intArray.forEach { bos.write(it) }
        }
    }

    fun writeBooleanArray(
        fileOutputStream: FileOutputStream,
        boolArray: BooleanArray
    ) {
        BufferedOutputStream(fileOutputStream).use { bos ->
            bos.write(boolArray.size)
            boolArray.forEach { bos.write(if (it) 1 else 0) }
        }
    }

    fun readShortArray(
        inputStream: InputStream
    ): ShortArray {
        DataInputStream(BufferedInputStream(inputStream)).use { bis ->
            val shortArraySize = bis.readInt()
            return ShortArray(shortArraySize) { bis.readShort() }
        }
    }

    fun readCharArray(inputStream: InputStream): CharArray {
        inputStream.use { bis ->
            val charArraySize = bis.read()
            return CharArray(charArraySize) { bis.read().toChar() }
        }
    }

    fun readIntArray(fileInputStream: FileInputStream): IntArray {
        BufferedInputStream(fileInputStream).use { bis ->
            val intArraySize = bis.read()
            return IntArray(intArraySize) { bis.read() }
        }
    }

    fun readBooleanArray(fileInputStream: FileInputStream): BooleanArray {
        BufferedInputStream(fileInputStream).use { bis ->
            val boolArraySize = bis.read()
            return BooleanArray(boolArraySize) { bis.read() != 0 }
        }
    }

}