package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.Node

/** A query-local, additive transition cost shared by forward pruning and backward search. */
fun interface LatticeEdgeCost {
    fun cost(previous: Node, current: Node): Int
}
