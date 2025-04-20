package com.kazumaproject.markdownhelperkeyboard.converter.english

import com.kazumaproject.markdownhelperkeyboard.converter.english.prefix.EnglishNode
import java.util.ArrayDeque
import java.util.Queue

class EnglishLOUDSConverter {
    fun convert(
        rootNode: EnglishNode,
    ): EnglishLOUDS {

        val louds = EnglishLOUDS()
        val queue: Queue<EnglishNode> = ArrayDeque()
        queue.add(rootNode)

        while (!queue.isEmpty()) {
            processQueue(queue, louds)
        }
        return louds
    }

    private fun processQueue(queue: Queue<EnglishNode>, louds: EnglishLOUDS) {
        val node: EnglishNode = queue.poll()
        if (node.hasChild()) {
            node.children.forEach { entry ->
                queue.add(entry.value.second)
                louds.apply {
                    LBSTemp.add(true)
                    labels.add(entry.key)
                    isLeafTemp.add(entry.value.second.isWord)
                    if (entry.value.second.isWord) {
                        costList.add(entry.value.second.termId.toShort())
                    }
                }
            }
        }

        louds.apply {
            LBSTemp.add(false)
            isLeafTemp.add(false)
        }
    }
}