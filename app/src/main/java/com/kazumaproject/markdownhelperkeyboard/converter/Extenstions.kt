package com.kazumaproject

import java.io.InputStream
import java.nio.ByteBuffer
import java.text.Normalizer
import java.util.BitSet


fun List<Boolean>.toBitSet(): BitSet {
    val bitSet = BitSet(this.size)
    this.forEachIndexed { index, value ->
        if (value) {
            bitSet.set(index, true)
        }
    }
    return bitSet
}

fun String.hiraToKata() =
    this.map {
        when (it) {
            'ゔ' -> 'ヴ'
            'づ' -> 'ヅ'  // づ is handled here
            'ゐ' -> 'ヰ'
            'ゑ' -> 'ヱ'
            in 'ぁ'..'ん' -> (it.code + 0x60).toChar()  // 通常のひらがなは0x60を加算してカタカナに変換
            else -> it  // その他の文字はそのまま
        }
    }.joinToString("")


fun List<Char>.toByteArrayFromListChar(): ByteArray {
    return this.map { it.code }.toByteArray()
}

fun ByteArray.toListChar(): MutableList<Char> {
    return this.toListInt().map { it.toChar() }.toMutableList()
}

fun List<Int>.toByteArray(): ByteArray {
    val buffer = ByteBuffer.allocate(this.size * 4) // Each Int occupies 4 bytes
    this.forEach { buffer.putInt(it) }
    return buffer.array()
}

fun ByteArray.toListInt(): MutableList<Int> {
    val intList = mutableListOf<Int>()
    for (i in indices step 4) {
        val value = (this[i].toInt() shl 24) or
                ((this[i + 1].toInt() and 0xFF) shl 16) or
                ((this[i + 2].toInt() and 0xFF) shl 8) or
                (this[i + 3].toInt() and 0xFF)
        intList.add(value)
    }
    return intList
}

fun List<Short>.toByteArrayFromListShort(): ByteArray {
    val byteArray = ByteArray(this.size * 2) // Each Short occupies 2 bytes
    for (i in this.indices) {
        val shortValue = this[i]
        byteArray[i * 2] = (shortValue.toInt() shr 8).toByte() // High byte
        byteArray[i * 2 + 1] = shortValue.toByte() // Low byte
    }
    return byteArray
}

fun ByteArray.byteArrayToShortList(): List<Short> {
    val shortList = mutableListOf<Short>()
    for (i in indices step 2) {
        val highByte = this[i].toInt() and 0xFF
        val lowByte = this[i + 1].toInt() and 0xFF
        val shortValue = (highByte shl 8) or lowByte
        shortList.add(shortValue.toShort())
    }
    return shortList
}

fun BitSet.toBooleanList(): List<Boolean> {
    return (0 until this.size()).map { this[it] }
}

fun List<Int>.toBitSetExtension(): BitSet {
    val bitSet = BitSet()
    this.forEach { bitSet.set(it) }
    return bitSet
}

fun BooleanArray.boolArrayToBitSet(): BitSet {
    val bitSet = BitSet(this.size)
    this.forEachIndexed { index, value ->
        if (value) {
            bitSet.set(index)
        }
    }
    return bitSet
}

fun readCharArrayFromBytes(
    inputStream: InputStream
): CharArray {
    val byteArray = inputStream.readBytes()
    val byteBuffer = ByteBuffer.wrap(byteArray)
    val charArray = CharArray(byteArray.size / 2)
    byteBuffer.asCharBuffer().get(charArray)
    return charArray
}

fun BitSet.toBooleanArray(): BooleanArray {
    return BooleanArray(this.length()) { this[it] }
}

fun List<String>.addingCommonPrefixList(): List<String> {
    val modifiedStrings = mutableListOf<String>()
    for (s in this) {
        modifiedStrings.add(s)
        when {
            s.contains('た') -> modifiedStrings.add(s.replace('た', 'だ'))
            s.contains('つ') -> modifiedStrings.add(s.replace('つ', 'っ'))
            s.contains('や') -> modifiedStrings.add(s.replace('や', 'ゃ'))
            s.contains('ゆ') -> modifiedStrings.add(s.replace('ゆ', 'ゅ'))
            s.contains('よ') -> modifiedStrings.add(s.replace('よ', 'ょ'))
        }
    }
    return modifiedStrings
}

fun List<String>.addingStringToListForCommonPrefix(): List<String> {
    val result = mutableListOf<String>()

    for (str in this) {
        val length = str.length
        val numCombinations = 1 shl length

        for (i in 0 until numCombinations) {
            val charArray = CharArray(length)
            for (j in 0 until length) {
                charArray[j] = if ((i shr j) and 1 == 1) {
                    when (str[j]) {

                        'か' -> 'が'
                        'き' -> 'ぎ'
                        'く' -> 'ぐ'
                        'け' -> 'げ'
                        'こ' -> 'ご'

                        'さ' -> 'ざ'
                        'し' -> 'じ'
                        'す' -> 'ず'
                        'せ' -> 'ぜ'
                        'そ' -> 'ぞ'

                        'た' -> 'だ'
                        'ち' -> 'ぢ'
                        'つ' -> 'づ'
                        'て' -> 'で'
                        'と' -> 'ど'

                        'は' -> 'ば'
                        'ひ' -> 'び'
                        'ふ' -> 'ぶ'
                        'へ' -> 'べ'
                        'ほ' -> 'ぼ'

                        'や' -> 'ゃ'
                        'ゆ' -> 'ゅ'
                        'よ' -> 'ょ'

                        else -> str[j]
                    }
                } else {
                    str[j]
                }
            }
            result.add(String(charArray))
        }

    }
    return result
}

fun BooleanArray.preprocessLBSIntoBooleanArray(): IntArray{
    val prefixSum = IntArray(this.size + 1)
    for (i in this.indices) {
        prefixSum[i + 1] = prefixSum[i] + if (this[i]) 0 else 1
    }
    return prefixSum
}

fun String.convertFullWidthToHalfWidth(): String {
    return Normalizer.normalize(this, Normalizer.Form.NFKC)
}
