package com.kazumaproject.markdownhelperkeyboard.converter.english.prefix

data class EnglishNode(
    val c: Char = ' ',
    var id: Int = -1,
    val children: MutableMap<Char, Pair<Int, EnglishNode>> = mutableMapOf(),
    var isWord: Boolean = false,
    var termId: Int = -1
) {
    fun hasChild(): Boolean = children.isNotEmpty()

    fun hasChildren(c: Char): Boolean = children.containsKey(c)

    fun getChildren(c: Char): EnglishNode? {
        return if (!hasChildren(c)) null else children[c]?.second
    }

    fun getChildNodeId(c: Char): Int? {
        return if (!hasChildren(c)) null else children[c]?.first
    }

    fun getChildrenSize(): Int = children.size

    fun addChildren(node: EnglishNode) {
        if (!hasChildren(node.c)) children[node.c] = Pair(node.id, node)
    }

    fun canDelete(): Boolean = children.isEmpty()

    fun convertToString() = "$c ($isWord $id) -> [${children.values}]"

    override fun toString(): String {
        return "$c ($isWord, $id) -> [$children]"
    }
}
