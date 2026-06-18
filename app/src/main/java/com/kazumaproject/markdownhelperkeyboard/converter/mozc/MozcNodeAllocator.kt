package com.kazumaproject.markdownhelperkeyboard.converter.mozc

class MozcNodeAllocator {
    private val nodes = mutableListOf<MozcNode>()

    fun newNode(): MozcNode = MozcNode().also(nodes::add)

    fun cloneNode(node: MozcNode): MozcNode = node.shallowCopyWithoutPath().also(nodes::add)

    fun clear() {
        nodes.clear()
    }
}
