package com.kazumaproject.markdownhelperkeyboard.converter.bitset

import java.util.BitSet

class BitSetWithRankSelect (bitSet: BitSet) {
     val rank1Array: IntArray
     val rank0Array: IntArray
     val select1Array: IntArray
     val select0Array: IntArray

    init {
        val n = bitSet.size()
        rank1Array = IntArray(n + 1)
        rank0Array = IntArray(n + 1)
        val ones = mutableListOf<Int>()
        val zeros = mutableListOf<Int>()

        for (i in 0 until n) {
            rank1Array[i + 1] = rank1Array[i] + if (bitSet[i]) 1 else 0
            rank0Array[i + 1] = rank0Array[i] + if (!bitSet[i]) 1 else 0
            if (bitSet[i]) {
                ones.add(i)
            } else {
                zeros.add(i)
            }
        }

        select1Array = ones.toIntArray()
        select0Array = zeros.toIntArray()
    }

    fun rank1(index: Int): Int {
        return rank1Array[index + 1]
    }

    fun rank0(index: Int): Int {
        return rank0Array[index + 1]
    }

    fun select1(k: Int): Int {
        return if (k in select1Array.indices) select1Array[k] else -1
    }

    fun select0(k: Int): Int {
        return if (k in select0Array.indices) select0Array[k] else -1
    }
}