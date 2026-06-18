package com.kazumaproject.markdownhelperkeyboard.converter.mozc

class MozcLattice(
    val key: String,
    private val allocator: MozcNodeAllocator = MozcNodeAllocator(),
) {
    private val beginNodes = List(key.length + 1) { mutableListOf<MozcNode>() }
    private val endNodes = List(key.length + 1) { mutableListOf<MozcNode>() }

    val bosNode: MozcNode
    val eosNode: MozcNode

    init {
        bosNode = allocator.newNode().apply {
            rightId = 0
            leftId = 0
            key = ""
            value = "BOS"
            nodeType = MozcNodeType.BOS
            wordCost = 0
            totalCost = 0
            beginPos = 0
            endPos = 0
        }
        eosNode = allocator.newNode().apply {
            rightId = 0
            leftId = 0
            key = ""
            value = "EOS"
            nodeType = MozcNodeType.EOS
            wordCost = 0
            totalCost = Int.MAX_VALUE
            beginPos = this@MozcLattice.key.length
            endPos = this@MozcLattice.key.length
        }
        endNodes[0].add(bosNode)
        beginNodes[key.length].add(eosNode)
    }

    fun newNode(): MozcNode = allocator.newNode()

    fun insert(node: MozcNode) {
        require(node.beginPos in 0..key.length) { "beginPos out of range: ${node.beginPos}" }
        require(node.endPos in 0..key.length) { "endPos out of range: ${node.endPos}" }
        require(node.beginPos <= node.endPos) {
            "beginPos must be <= endPos: ${node.beginPos}, ${node.endPos}"
        }
        node.prev = null
        node.next = null
        node.totalCost = if (node.nodeType == MozcNodeType.BOS) 0 else Int.MAX_VALUE
        beginNodes[node.beginPos].add(node)
        endNodes[node.endPos].add(node)
    }

    fun beginNodes(pos: Int): List<MozcNode> = beginNodes.getOrNull(pos).orEmpty()

    fun endNodes(pos: Int): List<MozcNode> = endNodes.getOrNull(pos).orEmpty()

    fun allNodes(): Sequence<MozcNode> =
        beginNodes.asSequence().flatMap { it.asSequence() } + sequenceOf(bosNode)
}
