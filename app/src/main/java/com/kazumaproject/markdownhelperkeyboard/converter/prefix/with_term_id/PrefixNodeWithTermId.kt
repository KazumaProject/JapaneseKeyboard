package com.kazumaproject.prefix.with_term_id

/**
 *
 * @param c char in edge
 * @param id node id
 * @param children children node if exists
 * @param isWord Boolean in end of word
 * @param termId Ids by words
 *
 **/
data class PrefixNodeWithTermId(
    val c: Char = ' ',
    var id: Int = -1,
    val children: MutableMap<Char, Pair<Int, PrefixNodeWithTermId>> = mutableMapOf(),
    var isWord: Boolean = false,
    var termId: Int = -1
){
    fun hasChild(): Boolean = children.isNotEmpty()

    fun hasChildren(c: Char): Boolean = children.containsKey(c)

    fun getChildren(c: Char): PrefixNodeWithTermId?{
        return if (!hasChildren(c)) null else children[c]?.second
    }

    fun getChildNodeId(c: Char): Int?{
        return if (!hasChildren(c)) null else children[c]?.first
    }

    fun getChildrenSize(): Int = children.size

    fun addChildren(node: PrefixNodeWithTermId){
        if (!hasChildren(node.c)) children[node.c] = Pair(node.id,node)
    }

    fun canDelete(): Boolean = children.isEmpty()

    fun convertToString() = "$c ($isWord $id) -> [${children.values}]"

    override fun toString(): String {
        return "$c ($isWord, $id) -> [$children]"
    }
}