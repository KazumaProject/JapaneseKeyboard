package com.kazumaproject.markdownhelperkeyboard.converter.mozc

class MozcViterbi(
    private val connector: MozcConnector,
    private val trace: MozcConverterTrace? = null,
) {
    fun calculate(lattice: MozcLattice): Boolean {
        lattice.bosNode.totalCost = 0
        lattice.bosNode.prev = lattice.bosNode
        lattice.bosNode.next = null

        for (pos in 0..lattice.key.length) {
            for (rightNode in lattice.beginNodes(pos)) {
                if (rightNode.nodeType == MozcNodeType.BOS) continue
                rightNode.prev = null
                rightNode.next = null
                rightNode.totalCost = VERY_BIG_COST

                val constrainedPrev = rightNode.constrainedPrev
                if (constrainedPrev != null) {
                    trace?.constrainedViterbiCount = (trace?.constrainedViterbiCount ?: 0) + 1
                    if (constrainedPrev.prev != null && constrainedPrev.totalCost < VERY_BIG_COST) {
                        rightNode.prev = constrainedPrev
                        rightNode.totalCost = saturatedCost(
                            constrainedPrev.totalCost,
                            connector.getTransitionCost(constrainedPrev.rightId, rightNode.leftId),
                            rightNode.wordCost,
                        )
                    }
                    continue
                }

                var bestCost = VERY_BIG_COST
                var bestPrev: MozcNode? = null
                for (leftNode in lattice.endNodes(pos)) {
                    if (leftNode.prev == null || leftNode.totalCost >= VERY_BIG_COST) continue
                    val cost = saturatedCost(
                        leftNode.totalCost,
                        connector.getTransitionCost(leftNode.rightId, rightNode.leftId),
                        rightNode.wordCost,
                    )
                    if (cost < bestCost) {
                        bestCost = cost
                        bestPrev = leftNode
                    }
                }
                rightNode.prev = bestPrev
                rightNode.totalCost = bestCost
            }
        }

        if (lattice.eosNode.prev == null) return false
        var node: MozcNode? = lattice.eosNode
        while (node != null && node !== lattice.bosNode) {
            val prev = node.prev ?: return false
            prev.next = node
            node = prev
        }
        return true
    }

    private fun saturatedCost(a: Int, b: Int, c: Int): Int {
        val sum = a.toLong() + b.toLong() + c.toLong()
        return sum.coerceIn(Int.MIN_VALUE.toLong(), VERY_BIG_COST.toLong()).toInt()
    }

    companion object {
        const val VERY_BIG_COST: Int = Int.MAX_VALUE / 4
    }
}
