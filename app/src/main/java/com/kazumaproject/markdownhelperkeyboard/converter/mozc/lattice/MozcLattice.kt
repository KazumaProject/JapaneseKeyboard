package com.kazumaproject.markdownhelperkeyboard.converter.mozc.lattice

import com.kazumaproject.markdownhelperkeyboard.converter.mozc.model.MozcNode
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.model.MozcNodeType

class MozcLattice {
    lateinit var key: String
        private set

    private val beginNodes = mutableListOf<MutableList<MozcNode>>()
    private val endNodes = mutableListOf<MutableList<MozcNode>>()

    lateinit var bosNode: MozcNode
        private set

    lateinit var eosNode: MozcNode
        private set

    fun setKey(input: String, bosId: Short = 0) {
        key = input
        beginNodes.clear()
        endNodes.clear()
        repeat(input.length + 1) {
            beginNodes.add(mutableListOf())
            endNodes.add(mutableListOf())
        }
        bosNode = MozcNode(
            rid = bosId,
            lid = bosId,
            beginPos = 0,
            endPos = 0,
            wcost = 0,
            cost = 0,
            nodeType = MozcNodeType.BOS_NODE,
            key = "BOS",
            value = "BOS",
        )
        eosNode = MozcNode(
            rid = 0,
            lid = 0,
            beginPos = input.length,
            endPos = input.length,
            wcost = 0,
            cost = Int.MAX_VALUE,
            nodeType = MozcNodeType.EOS_NODE,
            key = "EOS",
            value = "EOS",
        )
        endNodes[0].add(bosNode)
        beginNodes[input.length].add(eosNode)
    }

    fun insert(node: MozcNode) {
        require(node.beginPos in beginNodes.indices)
        require(node.endPos in endNodes.indices)
        beginNodes[node.beginPos].add(node)
        endNodes[node.endPos].add(node)
    }

    fun beginNodes(pos: Int): List<MozcNode> = beginNodes[pos]

    fun endNodes(pos: Int): List<MozcNode> = endNodes[pos]
}
