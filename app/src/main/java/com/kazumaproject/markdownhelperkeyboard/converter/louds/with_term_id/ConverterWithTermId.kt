package com.kazumaproject.Louds.with_term_id

import com.kazumaproject.prefix.with_term_id.PrefixNodeWithTermId
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class ConverterWithTermId {

    fun convert(
        rootNode: PrefixNodeWithTermId,
    ): LOUDSWithTermId {

        val louds = LOUDSWithTermId()
        val queue: Queue<PrefixNodeWithTermId> = ArrayDeque()
        queue.add(rootNode)


        while (!queue.isEmpty()) {
            processQueue(queue, louds)
        }
        return louds
    }

    private val atomicInteger = AtomicInteger(1)

    private fun processQueue(queue: Queue<PrefixNodeWithTermId>, louds: LOUDSWithTermId) {
        val node: PrefixNodeWithTermId = queue.poll()
        if (node.hasChild()) {
            node.children.forEach { entry ->
                queue.add(entry.value.second)
                louds.apply {
                    LBSTemp.add(true)
                    nodeIds.add(atomicInteger.incrementAndGet())
                    labels.add(entry.key)
                    isLeafTemp.add(entry.value.second.isWord)
                    if (entry.value.second.isWord){
                        termIds.add(entry.value.second.termId)
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