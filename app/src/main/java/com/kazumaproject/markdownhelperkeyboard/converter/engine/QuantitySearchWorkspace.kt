package com.kazumaproject.markdownhelperkeyboard.converter.engine

/** Query scratch contains primitive values only; retaining it cannot retain an old lattice. */
internal class QuantitySearchWorkspace {
    val states = QuantityIntRows(13)
    val edges = QuantityIntRows(3)
    val local = QuantityIntRows(6)
    val stateIndex = QuantityLongIndex()
    val localIndex = QuantityLongIndex()
    var stateHeads = IntArray(0)
    var localHeads = IntArray(0)

    fun reset(length: Int) {
        states.clear(); edges.clear(); local.clear(); stateIndex.clear(); localIndex.clear()
        if (stateHeads.size <= length) {
            stateHeads = IntArray(length + 1)
            localHeads = IntArray(length + 1)
        }
        stateHeads.fill(-1); localHeads.fill(-1)
    }
}

internal class QuantityIntRows(columns: Int) {
    private var data = Array(columns) { IntArray(128) }
    var size = 0
        private set
    fun clear() { size = 0 }
    fun add(): Int {
        if (size == data[0].size) data = Array(data.size) { data[it].copyOf(size * 2) }
        return size++
    }
    operator fun get(row: Int, column: Int): Int = data[column][row]
    operator fun set(row: Int, column: Int, value: Int) { data[column][row] = value }
}

/** Open addressing avoids boxed Long/Int keys in the transition loop. Keys are nonnegative. */
internal class QuantityLongIndex {
    private var keys = LongArray(256) { -1L }
    private var values = IntArray(256)
    private var count = 0
    fun clear() { keys.fill(-1L); count = 0 }
    private fun slot(key: Long): Int {
        var hash = key
        hash = (hash xor (hash ushr 33)) * -49064778989728563L
        hash = (hash xor (hash ushr 33)) * -4265267296055464877L
        var at = (hash xor (hash ushr 33)).toInt() and (keys.size - 1)
        while (keys[at] != -1L && keys[at] != key) at = (at + 1) and (keys.size - 1)
        return at
    }
    operator fun get(key: Long): Int {
        val at = slot(key)
        return if (keys[at] == key) values[at] else -1
    }
    operator fun set(key: Long, value: Int) {
        if ((count + 1) * 2 >= keys.size) {
            val oldKeys = keys; val oldValues = values
            keys = LongArray(oldKeys.size * 2) { -1L }; values = IntArray(keys.size); count = 0
            for (i in oldKeys.indices) if (oldKeys[i] != -1L) set(oldKeys[i], oldValues[i])
        }
        val at = slot(key)
        if (keys[at] == -1L) count++
        keys[at] = key; values[at] = value
    }
}
