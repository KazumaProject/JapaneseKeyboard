package com.kazumaproject.markdownhelperkeyboard.converter.english.prefix

import java.util.concurrent.atomic.AtomicInteger

class EnglishNodeTree {
    var root: EnglishNode = EnglishNode()
    private val atomicInteger = AtomicInteger(1)
    private val termIdAtomicInteger = AtomicInteger(1)

    /**
     * @param word
     */
    fun insert(word: String, cost: Int) {
        var current: EnglishNode = root
        for (i in word.indices) {
            current = if (current.hasChildren(word[i])) {
                current.getChildren(word[i])!!
            } else {
                val id = atomicInteger.incrementAndGet()
                val node = EnglishNode(
                    c = word[i],
                    id = id,
                    termId = cost
                )
                current.addChildren(node)
                node
            }
        }
        current.isWord = true
    }

    fun find(word: String): EnglishNode? {
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

    fun delete(word: CharArray): Boolean {
        return delete(word, root, 0)
    }

    private fun delete(word: CharArray, node: EnglishNode?, wIndex: Int): Boolean {
        var index = wIndex
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

    fun getTermIdSize(): Int = termIdAtomicInteger.get()
}