package com.kazumaproject.prefix

data class PrefixNode(
    val c: Char = ' ',
    var id: Int = -1,
    val children: MutableMap<Char, Pair<Int, PrefixNode>> = mutableMapOf(),
    var isWord: Boolean = false,
){

    fun hasChild(): Boolean = children.isNotEmpty()

    fun hasChildren(c: Char): Boolean = children.containsKey(c)

    fun getChildren(c: Char): PrefixNode?{
        return if (!hasChildren(c)) null else children[c]?.second
    }

    fun getChildNodeId(c: Char): Int?{
        return if (!hasChildren(c)) null else children[c]?.first
    }

    fun getChildrenSize(): Int = children.size

    fun addChildren(node: PrefixNode){
        if (!hasChildren(node.c)) children[node.c] = Pair(node.id,node)
    }

    fun canDelete(): Boolean = children.isEmpty()

    override fun toString(): String {
        return "$c ($isWord, $id) -> [$children]"
    }

}
