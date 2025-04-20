package com.kazumaproject.markdownhelperkeyboard.converter.english

import com.kazumaproject.bitset.rank0
import com.kazumaproject.bitset.rank1
import com.kazumaproject.bitset.select0
import com.kazumaproject.bitset.select1
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.toBitSet
import java.io.IOException
import java.io.ObjectInput
import java.io.ObjectOutput
import java.util.BitSet

class EnglishLOUDS {
    val LBSTemp: MutableList<Boolean> = arrayListOf()
    var LBS: BitSet = BitSet()
    var labels: MutableList<Char> = arrayListOf()
    var costList: MutableList<Short> = arrayListOf()
    var costListSave: ShortArray = shortArrayOf()
    var isLeaf: BitSet = BitSet()
    val isLeafTemp: MutableList<Boolean> = arrayListOf()

    init {
        LBSTemp.apply {
            add(true)
            add(false)
        }
        labels.apply {
            add(0, ' ')
            add(1, ' ')
        }
        isLeafTemp.apply {
            add(0, false)
            add(1, false)
        }
    }

    constructor()


    constructor(
        LBS: BitSet,
        labels: MutableList<Char>,
        isLeaf: BitSet,
        costList: ShortArray,
    ) {
        this.LBS = LBS
        this.labels = labels
        this.isLeaf = isLeaf
        this.costListSave = costList
    }

    fun convertListToBitSet() {
        LBS = LBSTemp.toBitSet()
        LBSTemp.clear()
        isLeaf = isLeafTemp.toBitSet()
        isLeafTemp.clear()
    }

    fun getLetter(nodeIndex: Int): String {
        val list = mutableListOf<Char>()
        val firstNodeId = LBS.rank1(nodeIndex)
        val firstChar = labels[firstNodeId]
        list.add(firstChar)
        var parentNodeIndex = LBS.select1(LBS.rank0(nodeIndex))
        while (parentNodeIndex != 0) {
            val parentNodeId = LBS.rank1(parentNodeIndex)
            val pair = labels[parentNodeId]
            list.add(pair)
            parentNodeIndex = LBS.select1(LBS.rank0(parentNodeIndex))
            if (parentNodeId == 0) return ""
        }
        return list.toList().reversed().joinToString("")
    }

    fun getLetterByNodeId(nodeId: Int): String {
        val list = mutableListOf<Char>()
        var parentNodeIndex = LBS.select1(nodeId)
        while (parentNodeIndex != 0) {
            val parentNodeId = LBS.rank1(parentNodeIndex)
            val pair = labels[parentNodeId]
            list.add(pair)
            parentNodeIndex = LBS.select1(LBS.rank0(parentNodeIndex))
        }
        return list.toList().reversed().joinToString("")
    }

    fun getNodeIndex(s: String): Int {
        return search(2, s.toCharArray(), 0)
    }

    fun getNodeId(s: String): Int {
        return LBS.rank0(getNodeIndex(s))
    }

    fun getTermId(nodeIndex: Int): Short {
        val firstNodeId = isLeaf.rank1(nodeIndex) - 1
        if (firstNodeId < 0) return -1

        //val firstTermId = termIds[firstNodeId]
        val firstTermId = costListSave[firstNodeId].toShort()
        return firstTermId
    }

    private fun firstChild(pos: Int): Int {
        LBS.apply {
            val y = select0(rank1(pos)) + 1
            return if (!this[y]) -1 else y
        }
    }

    private fun traverse(pos: Int, c: Char): Int {
        var childPos = firstChild(pos)
        if (childPos == -1) return -1
        while (LBS[childPos]) {
            if (c == labels[LBS.rank1(childPos)]) {
                return childPos
            }
            childPos += 1
        }
        return -1
    }

    fun commonPrefixSearch(str: String): List<String> {
        val resultTemp: MutableList<Char> = mutableListOf()
        val result: MutableList<String> = mutableListOf()
        var n = 0
        str.forEachIndexed { _, c ->
            n = traverse(n, c)
            val index = LBS.rank1(n)
            if (n == -1) return@forEachIndexed
            if (index >= labels.size) return result
            resultTemp.add(labels[index])
            if (isLeaf[n]) {
                val tempStr = resultTemp.joinToString("")
                if (result.size >= 1) {
                    val resultStr = result[0] + tempStr
                    result.add(resultStr)
                } else {
                    result.add(tempStr)
                    resultTemp.clear()
                }
            }
        }
        return result.toList()
    }

    fun predictiveSearch(prefix: String, limit: Int = Int.MAX_VALUE): List<String> {
        val results = mutableListOf<String>()
        val sb = StringBuilder()

        // 1) Walk down to the node matching the prefix
        var node = 0
        for (c in prefix) {
            node = traverse(node, c)
            if (node < 0) return results
            val idx = LBS.rank1(node)
            if (idx >= labels.size) return results
            sb.append(labels[idx])
        }

        // 2) Collect completions, but stop once we've reached limit
        collectWords(node, sb, results, limit)
        return results
    }

    /**
     * Depth‐first collect all words under [pos], up to [limit].
     */
    private fun collectWords(
        pos: Int,
        prefix: StringBuilder,
        results: MutableList<String>,
        limit: Int
    ) {
        if (results.size >= limit) return

        // If this node is a leaf, emit the current prefix
        if (isLeaf[pos]) {
            results.add(prefix.toString())
            if (results.size >= limit) return
        }

        // Iterate children in LOUDS order
        var child = firstChild(pos)
        while (child >= 0 && LBS[child] && results.size < limit) {
            val idx = LBS.rank1(child)
            if (idx >= labels.size) break

            // extend the prefix, recurse, then backtrack
            prefix.append(labels[idx])
            collectWords(child, prefix, results, limit)
            prefix.deleteCharAt(prefix.length - 1)

            child += 1
        }
    }

    fun getNodeIndex(
        s: String,
        succinctBitVector: SuccinctBitVector,
    ): Int {
        return search(
            2,
            s.toCharArray(),
            0,
            succinctBitVector,
        )
    }

    private tailrec fun search(
        index: Int,
        chars: CharArray,
        wordOffset: Int,
        succinctBitVector: SuccinctBitVector,
    ): Int {
        var currentIndex = index
        var charIndex = succinctBitVector.rank1(currentIndex)
        val charCount = chars.size

        while (currentIndex < LBS.size() && LBS[currentIndex]) {
            val currentChar = chars[wordOffset]
            val currentLabel = labels[charIndex]

            if (currentChar == currentLabel) {
                if (wordOffset + 1 == charCount) {
                    return if (isLeaf[currentIndex]) currentIndex else currentIndex
                }
                val nextIndex = succinctBitVector.select0(charIndex) + 1
                return search(
                    nextIndex,
                    chars,
                    wordOffset + 1,
                    succinctBitVector,
                )
            }
            currentIndex++
            charIndex++
        }
        return -1
    }

    fun getTermId(
        nodeIndex: Int,
        succinctBitVector: SuccinctBitVector
    ): Short {
        val firstNodeId: Int = succinctBitVector.rank1(nodeIndex) - 1
        if (firstNodeId < 0) return -1
        return costListSave[firstNodeId]
    }

    private fun indexOfLabel(label: Int, prefixSum: IntArray): Int {
        var low = 0
        var high = prefixSum.size - 1

        while (low < high) {
            val mid = (low + high) / 2
            if (prefixSum[mid] < label) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        return low
    }

    fun commonPrefixSearch(str: String, succinctBitVector: SuccinctBitVector): List<String> {
        val result = mutableListOf<String>()
        val resultTemp = StringBuilder()
        var n = 0
        for (c in str) {
            n = traverse(n, c, succinctBitVector)
            if (n < 0) break
            val index = succinctBitVector.rank1(n)
            if (index >= labels.size) break
            resultTemp.append(labels[index])
            if (isLeaf[n]) {
                result.add(resultTemp.toString())
            }
        }
        return result
    }

    /**
     * Predictive search using LOUDS + SuccinctBitVector, capped at [limit] results.
     */
    fun predictiveSearch(
        prefix: String,
        succinctBitVector: SuccinctBitVector,
        limit: Int = Int.MAX_VALUE
    ): List<String> {
        val results = mutableListOf<String>()
        val sb = StringBuilder()
        var node = 0
        for (c in prefix) {
            node = traverse(node, c, succinctBitVector)
            if (node < 0) return results
            val idx = succinctBitVector.rank1(node)
            if (idx >= labels.size) return results
            sb.append(labels[idx])
        }
        collectWords(node, sb, results, limit, succinctBitVector)
        return results
    }

    private fun traverse(pos: Int, c: Char, succinctBitVector: SuccinctBitVector): Int {
        var childPos = firstChild(pos, succinctBitVector)
        while (childPos >= 0 && LBS[childPos]) {
            if (c == labels[succinctBitVector.rank1(childPos)]) {
                return childPos
            }
            childPos++
        }
        return -1
    }

    /**
     * Depth‐first collect all words under [pos], up to [limit].
     */
    private fun collectWords(
        pos: Int,
        prefix: StringBuilder,
        results: MutableList<String>,
        limit: Int,
        succinctBitVector: SuccinctBitVector
    ) {
        if (results.size >= limit) return
        if (isLeaf[pos]) {
            results.add(prefix.toString())
            if (results.size >= limit) return
        }
        var child = firstChild(pos, succinctBitVector)
        while (child >= 0 && results.size < limit) {
            val idx = succinctBitVector.rank1(child)
            if (idx >= labels.size) break
            prefix.append(labels[idx])
            collectWords(child, prefix, results, limit, succinctBitVector)
            prefix.deleteCharAt(prefix.lastIndex)
            child += 1
        }
    }

    private fun firstChild(pos: Int, succinctBitVector: SuccinctBitVector): Int {
        val rank1 = succinctBitVector.rank1(pos)
        val select0 = succinctBitVector.select0(rank1) + 1
        return if (select0 < 0 || !LBS[select0]) -1 else select0
    }

    private fun search(index: Int, chars: CharArray, wordOffset: Int): Int {
        var index2 = index
        var wordOffset2 = wordOffset
        var charIndex = LBS.rank1(index2)
        while (LBS[index2]) {
            if (chars[wordOffset2] == labels[charIndex]) {
                if (isLeaf[index2] && wordOffset2 + 1 == chars.size) {
                    return index2
                } else if (wordOffset2 + 1 == chars.size) {
                    return index2
                }
                return search(indexOfLabel(charIndex), chars, ++wordOffset2)
            } else {
                index2++
            }
            charIndex++
        }
        return -1
    }

    private fun indexOfLabel(label: Int): Int {
        var count = 0
        var i = 0
        while (i < LBS.size()) {
            if (!LBS[i]) {
                if (++count == label) {
                    break
                }
            }
            i++
        }

        return i + 1
    }

    fun writeExternal(out: ObjectOutput) {
        try {
            out.apply {
                writeObject(LBS)
                writeObject(isLeaf)
                writeObject(labels.toCharArray())
                writeObject(costList.toShortArray())
                flush()
                close()
            }
        } catch (e: IOException) {
            println(e.stackTraceToString())
        }
    }

    fun readExternal(objectInput: ObjectInput): EnglishLOUDS {
        objectInput.apply {
            try {
                LBS = objectInput.readObject() as BitSet
                isLeaf = objectInput.readObject() as BitSet
                labels = (objectInput.readObject() as CharArray).toMutableList()
                costListSave = (objectInput.readObject() as ShortArray)
                close()
            } catch (e: Exception) {
                println(e.stackTraceToString())
            }
        }
        return EnglishLOUDS(LBS, labels, isLeaf, costListSave)
    }
}