package com.kazumaproject.markdownhelperkeyboard.converter.louds

import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import java.util.BitSet

/** A resumable DFS cursor; memory is proportional to the longest reading, not word count. */
internal class LoudsTermIterator(
    private val bits: BitSet,
    private val lbs: SuccinctBitVector,
    private val labelCount: Int,
    private val labelAt: (Int) -> Char,
    private val termAt: (Int) -> Int,
) : Iterator<Pair<String, Int>> {
    private data class Frame(val node: Int, var child: Int = -1, var visited: Boolean = false)
    private val stack = ArrayDeque<Frame>().apply { addLast(Frame(0)) }
    private val reading = StringBuilder()
    private var pending: Pair<String, Int>? = null

    override fun hasNext(): Boolean {
        if (pending != null) return true
        while (stack.isNotEmpty()) {
            val frame = stack.last()
            if (!frame.visited) {
                frame.visited = true
                frame.child = loudsChildPosition(bits, frame.node, lbs.select0(lbs.rank1(frame.node)))
                val term = termAt(frame.node)
                if (term >= 0) {
                    pending = reading.toString() to term
                    return true
                }
            }
            val child = frame.child
            val label = if (child >= 0 && bits[child]) lbs.rank1(child) else labelCount
            if (label < labelCount) {
                frame.child++
                reading.append(labelAt(label))
                stack.addLast(Frame(child))
            } else {
                stack.removeLast()
                if (reading.isNotEmpty()) reading.deleteCharAt(reading.lastIndex)
            }
        }
        return false
    }

    override fun next(): Pair<String, Int> {
        if (!hasNext()) throw NoSuchElementException()
        return pending!!.also { pending = null }
    }
}
