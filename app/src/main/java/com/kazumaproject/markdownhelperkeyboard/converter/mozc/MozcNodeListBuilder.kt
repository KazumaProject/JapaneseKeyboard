package com.kazumaproject.markdownhelperkeyboard.converter.mozc

class MozcNodeListBuilder {
    private val nodes = mutableListOf<MozcNode>()

    fun add(node: MozcNode) {
        nodes.add(node)
    }

    fun result(): List<MozcNode> = nodes

    fun insertInto(lattice: MozcLattice): Int {
        nodes.forEach(lattice::insert)
        return nodes.size
    }
}
