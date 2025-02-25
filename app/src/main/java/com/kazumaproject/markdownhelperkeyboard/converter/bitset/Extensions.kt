package com.kazumaproject.bitset

import java.nio.IntBuffer
import java.util.BitSet

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

fun BitSet.rank1GetIntArray(): IntArray {
    val n = this.size()
    val rank = IntArray(n + 1)
    for (i in 1..n) {
        rank[i] = rank[i - 1] + if (this[i - 1]) 1 else 0
    }
    return rank
}

// Extension function for rank0 in O(1) time complexity
fun BitSet.rank0GetIntArray(): IntArray {
    val n = this.size()
    val rank = IntArray(n + 1)
    for (i in 1..n) {
        rank[i] = rank[i - 1] + if (this[i - 1]) 0 else 1
    }
    return rank
}

fun BitSet.rank1GetShortArray(): ShortArray {
    val n = this.size()
    val rank = ShortArray(n + 1)
    for (i in 1..n) {
        rank[i] = (rank[i - 1] + if (this[i - 1]) 1 else 0).toShort()
    }
    return rank
}

// Extension function for rank0 in O(1) time complexity
fun BitSet.rank0GetShortArray(): ShortArray {
    val n = this.size()
    val rank = ShortArray(n + 1)
    for (i in 1..n) {
        rank[i] = (rank[i - 1] + if (this[i - 1]) 0 else 1).toShort()
    }
    return rank
}

// Extension function to get the number of 1s up to and including index i
fun BitSet.rank1Common(i: Int, rank1: IntArray): Int {
    return rank1[i + 1]
}

// Extension function to get the number of 0s up to and including index i
fun BitSet.rank0Common(i: Int, rank0: IntArray): Int {
    return rank0[i + 1]
}

// Extension function for select1 in O(log n) time complexity
fun BitSet.select1Common(j: Int, rank1: IntArray): Int {
    var low = 0
    var high = this.size() - 1
    while (low <= high) {
        val mid = (low + high) / 2
        if (rank1[mid + 1] > j) {
            high = mid - 1
        } else if (rank1[mid + 1] < j) {
            low = mid + 1
        } else {
            if (this[mid]) return mid
            high = mid - 1
        }
    }
    return -1
}

// Extension function for select0 in O(log n) time complexity
fun BitSet.select0Common(j: Int, rank0: IntArray): Int {
    var low = 0
    var high = this.size() - 1
    while (low <= high) {
        val mid = (low + high) / 2
        if (rank0[mid + 1] > j) {
            high = mid - 1
        } else if (rank0[mid + 1] < j) {
            low = mid + 1
        } else {
            if (!this[mid]) return mid
            high = mid - 1
        }
    }
    return -1
}

fun BitSet.select0Common(j: Int, rank0: IntBuffer): Int {
    var low = 0
    var high = this.size() - 1
    while (low <= high) {
        val mid = (low + high) / 2
        if (rank0[mid + 1] > j) {
            high = mid - 1
        } else if (rank0[mid + 1] < j) {
            low = mid + 1
        } else {
            if (!this[mid]) return mid
            high = mid - 1
        }
    }
    return -1
}

// Extension function to get the number of 1s up to and including index i
fun BitSet.rank1CommonShort(i: Int, rank1: ShortArray): Short {
    return rank1[i + 1]
}

// Extension function to get the number of 0s up to and including index i
fun BitSet.rank0CommonShort(i: Short, rank0: ShortArray): Short {
    return rank0[i + 1]
}

// Extension function for select1 in O(log n) time complexity
fun BitSet.select1CommonShort(j: Short, rank1: ShortArray): Short {
    var low = 0
    var high = this.size() - 1
    while (low <= high) {
        val mid = (low + high) / 2
        if (rank1[mid + 1] > j) {
            high = mid - 1
        } else if (rank1[mid + 1] < j) {
            low = mid + 1
        } else {
            if (this[mid]) return mid.toShort()
            high = mid - 1
        }
    }
    return -1
}

// Extension function for select0 in O(log n) time complexity
fun BitSet.select0CommonShort(j: Short, rank0: ShortArray): Short {
    var low = 0
    var high = this.size() - 1
    while (low <= high) {
        val mid = (low + high) / 2
        if (rank0[mid + 1] > j) {
            high = mid - 1
        } else if (rank0[mid + 1] < j) {
            low = mid + 1
        } else {
            if (!this[mid]) return mid.toShort()
            high = mid - 1
        }
    }
    return -1
}
