package com.kazumaproject.counter

/** CSR array trie: N+1 edge/output offsets, UTF-16 edge labels, integer targets. */
internal class CounterTrie(val edges: IntArray, val labels: CharArray, val targets: IntArray, val postings: IntArray, val outputs: IntArray) {
    fun next(state: Int, letter: Char): Int {
        val start = edges[state]; val end = edges[state + 1]
        if (end - start <= 4) {
            for (edge in start until end) if (labels[edge] == letter) return targets[edge]
        } else {
            var low = start; var high = end - 1
            while (low <= high) {
                val middle = (low + high) ushr 1
                val comparison = labels[middle].compareTo(letter)
                if (comparison == 0) return targets[middle]
                if (comparison < 0) low = middle + 1 else high = middle - 1
            }
        }
        return -1
    }
    fun exact(input: String): Int {
        var state = 0
        for (letter in input) { state = next(state, letter); if (state < 0) return -1 }
        return state
    }
    fun verify(outputLimit: Int) {
        require(edges.size >= 2 && edges.size == postings.size && labels.size == targets.size)
        require(edges[0] == 0 && postings[0] == 0 && edges.last() == labels.size && postings.last() == outputs.size)
        val incoming = IntArray(edges.size - 1)
        for (state in incoming.indices) {
            require(edges[state] in 0..labels.size && edges[state + 1] in edges[state]..labels.size)
            require(postings[state] in 0..outputs.size && postings[state + 1] in postings[state]..outputs.size)
            for (edge in edges[state] until edges[state + 1]) {
                require(targets[edge] > state && targets[edge] < incoming.size) { "Invalid/cyclic trie edge" }
                require(edge == edges[state] || labels[edge - 1] < labels[edge]) { "Unsorted trie edges" }
                incoming[targets[edge]]++
            }
        }
        require(incoming[0] == 0 && incoming.drop(1).all { it == 1 }) { "Unreachable/shared trie nodes" }
        require(outputs.all { it in 0 until outputLimit })
    }
    companion object {
        fun build(entries: List<Pair<String, Int>>, reversed: Boolean = false): CounterTrie {
            class Node { val children = sortedMapOf<Char, Node>(); val outputs = sortedSetOf<Int>() }
            val root = Node()
            for ((key, output) in entries) {
                require(key.isNotEmpty()); var node = root
                for (letter in if (reversed) key.reversed() else key) node = node.children.getOrPut(letter) { Node() }
                node.outputs += output
            }
            val nodes = mutableListOf(root); val edges = mutableListOf<Int>(); val labels = mutableListOf<Char>()
            val targets = mutableListOf<Int>(); val postings = mutableListOf<Int>(); val outputs = mutableListOf<Int>()
            var index = 0
            while (index < nodes.size) {
                val node = nodes[index++]; edges += labels.size; postings += outputs.size
                for ((letter, child) in node.children) { labels += letter; targets += nodes.size; nodes += child }
                outputs += node.outputs
            }
            edges += labels.size; postings += outputs.size
            return CounterTrie(edges.toIntArray(), labels.toCharArray(), targets.toIntArray(), postings.toIntArray(), outputs.toIntArray())
        }
    }
}
