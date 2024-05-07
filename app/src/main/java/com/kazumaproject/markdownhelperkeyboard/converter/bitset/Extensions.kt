package com.kazumaproject.bitset

import java.util.*

fun BitSet.rank0(index: Int): Int {
    var count = 0
    for (i in 0..index) {
        if (!this[i]) {
            count++
        }
    }
    return count
}

fun BitSet.rank1(index: Int): Int {
    return index + 1 - rank0(index)
}

fun BitSet.select0(nodeId: Int): Int {
    var count = 0
    for (i in 0 until size()) {
        if (!this[i]) {
            count++
            if (count == nodeId) {
                return i
            }
        }
    }
    return -1 // Not found
}

fun BitSet.select1(nodeId: Int): Int {
    var count = 0
    for (i in 0 until size()) {
        if (this[i]) {
            count++
            if (count == nodeId) {
                return i
            }
        }
    }
    return -1 // Not found
}