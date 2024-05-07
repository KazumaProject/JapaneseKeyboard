package com.kazumaproject.prefix

import java.util.concurrent.atomic.AtomicInteger

class PrefixTree {

    var root: PrefixNode = PrefixNode()
    private val atomicInteger = AtomicInteger(1)

    /**
     * @param word
     */
    fun insert(word: String) {
        var current: PrefixNode = root
        for (i in word.indices) {
            if (current.hasChildren(word[i])) {
                current = current.getChildren(word[i])!!
            } else {
                val id = atomicInteger.incrementAndGet()
                val node = PrefixNode(
                    c = word[i],
                    id = id,
                )
                current.addChildren(node)
                current = node
            }
        }
        current.isWord = true
    }

    fun find(word: String): PrefixNode? {
        var current = root
        for (element in word) {
            if (current.hasChildren(element)) {
                current.getChildren(element)?.let { cNode ->
                    current = cNode
                }
            } else {
                return null
            }
        }
        return current
    }

    fun delete(word: CharArray): Boolean{
        return delete(word,root,0)
    }

    private fun delete(word: CharArray, node: PrefixNode?, wIndex: Int): Boolean {
        var index= wIndex
        if (index == word.size - 1) {
            val w = node!!.getChildren(word[index])
            w!!.isWord = false
            w.id = 0
            if (w.canDelete()) {
                node.children.remove(word[index])
                return true
            }
        } else {
            if (!node!!.hasChildren(word[index])) return false
            val canDelete = delete(word, node.getChildren(word[index]), ++index)
            if (canDelete) {
                if (node.getChildren(word[--index])!!.isWord) return false
                node.children.remove(word[index])
                return true
            }
        }
        return false
    }

    fun getNodeSize(): Int = atomicInteger.get()

}