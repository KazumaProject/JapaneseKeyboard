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
    startNode: Int = 0,
    prefix: String = "",
    private val includeChild: ((labelIndex: Int) -> Boolean)? = null,
) : Iterator<Pair<String, Int>> {
    private class Frame(
        var node: Int,
        var label: Int,
        var child: Int = -1,
        var childLabel: Int = 0,
        var visited: Boolean = false,
    ) {
        private var previousLabel = -1
        private var previousDelimiter = -1

        fun delimiter(bits: BitSet, lbs: SuccinctBitVector): Int {
            // DFS follows LOUDS label order within a depth. Consecutive labels
            // have consecutive zero delimiters in the level-order encoding.
            val position = if (previousDelimiter >= 0 && label == previousLabel + 1) {
                bits.nextClearBit(previousDelimiter + 1).takeIf { it < lbs.size() } ?: -1
            } else {
                lbs.select0(label)
            }
            previousLabel = label
            previousDelimiter = position
            return position
        }
    }
    private val frames = arrayListOf(Frame(startNode, lbs.rank1(startNode)))
    private var depth = 0
    private val reading = StringBuilder(prefix)
    private var pending: Pair<String, Int>? = null

    override fun hasNext(): Boolean {
        if (pending != null) return true
        while (depth >= 0) {
            val frame = frames[depth]
            if (!frame.visited) {
                frame.visited = true
                val delimiter = frame.delimiter(bits, lbs)
                frame.child = loudsChildPosition(bits, frame.node, delimiter)
                // The delimiter is the frame.label-th zero. Its first child
                // and consecutive siblings are one-bits with consecutive ranks.
                frame.childLabel = frame.child + 1 - frame.label
                val term = termAt(frame.node)
                if (term >= 0) {
                    pending = reading.toString() to term
                    return true
                }
            }
            val child = frame.child
            val label = frame.childLabel
            if (child >= 0 && label < labelCount && bits[child]) {
                frame.child++
                frame.childLabel++
                if (includeChild?.invoke(label) == false) continue
                reading.append(labelAt(label))
                depth++
                if (depth == frames.size) {
                    frames.add(Frame(child, label))
                } else {
                    val next = frames[depth]
                    next.node = child
                    next.label = label
                    next.visited = false
                }
            } else {
                depth--
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
