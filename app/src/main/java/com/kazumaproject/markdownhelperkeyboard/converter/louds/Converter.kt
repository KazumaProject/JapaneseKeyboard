package com.kazumaproject.Louds

import com.kazumaproject.prefix.PrefixNode
import java.util.ArrayDeque
import java.util.Queue


class Converter {

    fun convert(rootNode: PrefixNode): LOUDS {
        val louds = LOUDS()
        val queue: Queue<PrefixNode> = ArrayDeque()
        queue.add(rootNode)

        while (!queue.isEmpty()) {
            processQueue(queue, louds)
        }
        return louds
    }

    private fun processQueue(queue: Queue<PrefixNode>, louds: LOUDS) {
        val node: PrefixNode = queue.poll()

        if (node.hasChild()) {
            node.children.forEach { entry ->
                queue.add(entry.value.second)
                louds.apply {
                    LBSTemp.add(true)
                    labelsTemp.add(entry.key)
                    isLeafTemp.add(entry.value.second.isWord)
                }
            }
        }

        louds.apply {
            LBSTemp.add(false)
            isLeafTemp.add(false)
        }

    }
}