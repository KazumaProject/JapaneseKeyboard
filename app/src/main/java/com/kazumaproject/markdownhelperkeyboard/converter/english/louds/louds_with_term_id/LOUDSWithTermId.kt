package com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id

import com.kazumaproject.bitset.rank0
import com.kazumaproject.bitset.rank1
import com.kazumaproject.bitset.rank1Common
import com.kazumaproject.bitset.rank1CommonShort
import com.kazumaproject.bitset.select0Common
import com.kazumaproject.bitset.select0CommonShort
import com.kazumaproject.bitset.select1
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.toBitSet
import java.io.IOException
import java.io.ObjectInput
import java.io.ObjectOutput
import java.util.BitSet

class LOUDSWithTermId {

    val LBSTemp: MutableList<Boolean> = arrayListOf()
    var LBS: BitSet = BitSet()
    var labels: CharArray = charArrayOf()
    val labelsTemp: MutableList<Char> = arrayListOf()
    var termIds: MutableList<Int> = arrayListOf()
    var termIdsSaved: IntArray = intArrayOf()
    var isLeaf: BitSet = BitSet()
    val isLeafTemp: MutableList<Boolean> = arrayListOf()

    init {
        LBSTemp.apply {
            add(true)
            add(false)
        }
        labelsTemp.apply {
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
        labels: CharArray,
        isLeaf: BitSet,
        termIdsList: IntArray,
    ) {
        this.LBS = LBS
        this.labels = labels
        this.isLeaf = isLeaf
        this.termIdsSaved = termIdsList
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
        return list.toList().asReversed().joinToString("")
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
        return list.toList().asReversed().joinToString("")
    }

    fun contains(
        str: String,
        rank1Array: IntArray,
        rank0Array: IntArray
    ): Boolean {
        var currentNodeIndex = 0

        for (char in str) {
            currentNodeIndex = traverse(
                currentNodeIndex,
                char,
                rank0Array = rank0Array,
                rank1Array = rank1Array
            )
            if (currentNodeIndex == -1) return false
        }
        return isLeaf[currentNodeIndex]
    }


    fun getNodeIndex(
        s: String,
        rank1Array: IntArray,
        LBSInBoolArrayPreprocess: IntArray
    ): Int {
        return search(2, s.toCharArray(), 0, rank1Array, LBSInBoolArrayPreprocess)
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

    fun getNodeIndex(
        s: String,
        rank1Array: ShortArray,
        LBSInBoolArrayPreprocess: IntArray
    ): Int {
        return searchShortArray(
            2,
            s.toCharArray(),
            0,
            rank1Array,
            LBSInBoolArrayPreprocess
        )
    }

    fun getTermId(
        nodeIndex: Int,
        rank1Array: IntArray
    ): Int {
        val firstNodeId: Int = isLeaf.rank1Common(nodeIndex, rank1Array) - 1
        if (firstNodeId < 0) return -1
        return termIdsSaved[firstNodeId]
    }

    fun getTermId(
        nodeIndex: Int,
        succinctBitVector: SuccinctBitVector
    ): Int {
        val firstNodeId: Int = succinctBitVector.rank1(nodeIndex) - 1
        if (firstNodeId < 0) return -1
        return termIdsSaved[firstNodeId]
    }

    fun getTermIdShortArray(
        nodeIndex: Int,
        rank1Array: ShortArray
    ): Short {
        val firstNodeId: Int = isLeaf.rank1CommonShort(nodeIndex, rank1Array) - 1
        if (firstNodeId < 0) return -1
        val firstTermId: Int = termIdsSaved[firstNodeId]
        return firstTermId.toShort()
    }

    fun getTermIdShortArray(
        nodeIndex: Int,
        succinctBitVector: SuccinctBitVector
    ): Short {
        val firstNodeId: Int = succinctBitVector.rank1(nodeIndex) - 1
        if (firstNodeId < 0) return -1
        val firstTermId: Int = termIdsSaved[firstNodeId]
        return firstTermId.toShort()
    }


    private fun firstChildShortArray(
        pos: Int,
        rank0Array: ShortArray,
        rank1Array: ShortArray
    ): Int {
        val rank1Value = LBS.rank1CommonShort(pos, rank1Array)
        val select0 = LBS.select0CommonShort(rank1Value, rank0Array)
        val y = select0 + 1
        return if (!LBS[y]) -1 else y
    }

    private fun traverseShortArray(
        pos: Int,
        c: Char,
        rank0Array: ShortArray,
        rank1Array: ShortArray
    ): Int {
        var childPos = firstChildShortArray(pos, rank0Array, rank1Array)
        if (childPos < 0) return -1
        while (LBS[childPos]) {
            if (c == labels[LBS.rank1CommonShort(childPos, rank1Array).toInt()]) {
                return childPos
            }
            childPos += 1
        }
        return -1
    }

    private fun firstChild(pos: Int, rank0Array: IntArray, rank1Array: IntArray): Int {
        val y = LBS.select0Common(LBS.rank1Common(pos, rank1Array), rank0Array) + 1
        return if (y < 0 || !LBS[y]) -1 else y
    }

    private fun firstChild(pos: Int, succinctBitVector: SuccinctBitVector): Int {
        val rank1 = succinctBitVector.rank1(pos)
        val select0 = succinctBitVector.select0(rank1) + 1
        return if (select0 < 0 || !LBS[select0]) -1 else select0
    }

    private fun traverse(pos: Int, c: Char, rank0Array: IntArray, rank1Array: IntArray): Int {
        var childPos = firstChild(pos, rank0Array, rank1Array)
        while (childPos >= 0 && LBS[childPos]) {
            if (c == labels[LBS.rank1Common(childPos, rank1Array)]) {
                return childPos
            }
            childPos++
        }
        return -1
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

    fun commonPrefixSearch(str: String, rank0Array: IntArray, rank1Array: IntArray): List<String> {
        val result = mutableListOf<String>()
        val resultTemp = StringBuilder()
        var n = 0
        for (c in str) {
            n = traverse(n, c, rank0Array, rank1Array)
            if (n < 0) break
            val index = LBS.rank1Common(n, rank1Array)
            if (index >= labels.size) break
            resultTemp.append(labels[index])
            if (isLeaf[n]) {
                result.add(resultTemp.toString())
            }
        }
        return result
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

    private fun collectWords(
        pos: Int,
        prefix: StringBuilder,
        rank0Array: IntArray,
        rank1Array: IntArray,
        result: MutableList<String>
    ) {
        if (isLeaf[pos]) {
            result.add(prefix.toString())
        }
        var childPos = firstChild(pos, rank0Array, rank1Array)
        while (childPos >= 0 && LBS[childPos]) {
            val index = LBS.rank1Common(childPos, rank1Array)
            if (index >= labels.size) break
            prefix.append(labels[index])
            collectWords(childPos, prefix, rank0Array, rank1Array, result)
            prefix.deleteCharAt(prefix.length - 1)
            childPos++
        }
    }

    private fun collectWords(
        pos: Int,
        prefix: StringBuilder,
        rank0Array: ShortArray,
        rank1Array: ShortArray,
        result: MutableList<String>
    ) {
        if (isLeaf[pos]) {
            result.add(prefix.toString())
        }
        var childPos = firstChildShortArray(pos, rank0Array, rank1Array)
        while (childPos >= 0 && LBS[childPos]) {
            val index = LBS.rank1CommonShort(childPos, rank1Array)
            if (index >= labels.size) break
            prefix.append(labels[index.toInt()])
            collectWords(childPos, prefix, rank0Array, rank1Array, result)
            prefix.deleteCharAt(prefix.length - 1)
            childPos++
        }
    }

    private fun collectWords(
        pos: Int,
        prefix: StringBuilder,
        succinctBitVector: SuccinctBitVector,
        result: MutableList<String>
    ) {
        if (isLeaf[pos]) {
            result.add(prefix.toString())
        }
        var childPos = firstChild(pos, succinctBitVector)
        while (childPos >= 0 && LBS[childPos]) {
            val index = succinctBitVector.rank1(childPos)
            if (index >= labels.size) break
            prefix.append(labels[index])
            collectWords(childPos, prefix, succinctBitVector, result)
            prefix.deleteCharAt(prefix.length - 1)
            childPos++
        }
    }

    /**
     * Return up to [limit] completions of [prefix].
     */
    fun predictiveSearch(
        prefix: String,
        succinctBitVector: SuccinctBitVector,
        limit: Int
    ): List<String> {
        val result = mutableListOf<String>()
        val resultTemp = StringBuilder()
        var n = 0

        // walk the trie according to prefix
        for (c in prefix) {
            n = traverse(n, c, succinctBitVector)
            if (n < 0) return result
            val index = succinctBitVector.rank1(n)
            if (index >= labels.size) return result
            resultTemp.append(labels[index])
        }

        // collect words under node n, but stop at [limit]
        collectWords(n, resultTemp, succinctBitVector, result, limit)
        return result
    }

    /**
     * Recursively collect completions starting at [pos], appending to [prefix].
     * Stops once result.size >= limit.
     */
    private fun collectWords(
        pos: Int,
        prefix: StringBuilder,
        succinctBitVector: SuccinctBitVector,
        result: MutableList<String>,
        limit: Int
    ) {
        // stop immediately if we've hit the limit
        if (result.size >= limit) return

        // if this node marks a word end, add it
        if (isLeaf[pos]) {
            result.add(prefix.toString())
            if (result.size >= limit) return
        }

        // descend into children
        var childPos = firstChild(pos, succinctBitVector)
        while (childPos >= 0 && LBS[childPos] && result.size < limit) {
            val idx = succinctBitVector.rank1(childPos)
            if (idx >= labels.size) break

            // add edge label
            prefix.append(labels[idx])
            collectWords(childPos, prefix, succinctBitVector, result, limit)
            prefix.deleteCharAt(prefix.lastIndex)

            childPos++
        }
    }

    fun predictiveSearch(prefix: String, rank0Array: IntArray, rank1Array: IntArray): List<String> {
        val result = mutableListOf<String>()
        val resultTemp = StringBuilder()
        var n = 0
        for (c in prefix) {
            n = traverse(n, c, rank0Array, rank1Array)
            if (n < 0) return result // No match found
            val index = LBS.rank1Common(n, rank1Array)
            if (index >= labels.size) return result
            resultTemp.append(labels[index])
        }
        // Collect all words starting from the last matched node
        collectWords(n, resultTemp, rank0Array, rank1Array, result)
        return result
    }

    fun predictiveSearch(prefix: String, succinctBitVector: SuccinctBitVector): List<String> {
        val result = mutableListOf<String>()
        val resultTemp = StringBuilder()
        var n = 0
        for (c in prefix) {
            n = traverse(n, c, succinctBitVector)
            if (n < 0) return result // No match found
            val index = succinctBitVector.rank1(n)
            if (index >= labels.size) return result
            resultTemp.append(labels[index])
        }
        // Collect all words starting from the last matched node
        collectWords(n, resultTemp, succinctBitVector, result)
        return result
    }

    fun predictiveSearch(
        prefix: String,
        rank0Array: ShortArray,
        rank1Array: ShortArray
    ): List<String> {
        val result = mutableListOf<String>()
        val resultTemp = StringBuilder()
        var n = 0
        for (c in prefix) {
            n = traverseShortArray(n, c, rank0Array, rank1Array)
            if (n < 0) return result // No match found
            val index = LBS.rank1CommonShort(n, rank1Array)
            if (index >= labels.size) return result
            resultTemp.append(labels[index.toInt()])
        }
        // Collect all words starting from the last matched node
        collectWords(n, resultTemp, rank0Array, rank1Array, result)
        return result
    }

    fun commonPrefixSearchShortArray(
        str: String,
        rank0Array: ShortArray,
        rank1Array: ShortArray,
    ): List<String> {
        val resultTemp = StringBuilder()
        val result: MutableList<String> = mutableListOf()
        var n = 0
        for (c in str) {
            n = traverseShortArray(n, c, rank0Array, rank1Array)
            val index = LBS.rank1CommonShort(n, rank1Array)
            if (n < 0 || index >= labels.size) break

            resultTemp.append(labels[index.toInt()])
            if (isLeaf[n]) {
                result.add(resultTemp.toString())
            }
        }
        return result
    }

    private tailrec fun search(
        index: Int,
        chars: CharArray,
        wordOffset: Int,
        rank1Array: IntArray,
        LBSInBoolArrayPreprocess: IntArray
    ): Int {
        var currentIndex = index
        var charIndex = LBS.rank1Common(currentIndex, rank1Array)
        val charCount = chars.size

        while (currentIndex < LBS.size() && LBS[currentIndex]) {
            val currentChar = chars[wordOffset]
            val currentLabel = labels[charIndex]

            if (currentChar == currentLabel) {
                if (wordOffset + 1 == charCount) {
                    return if (isLeaf[currentIndex]) currentIndex else currentIndex
                }
                val nextIndex = indexOfLabel(charIndex, LBSInBoolArrayPreprocess)
                return search(
                    nextIndex,
                    chars,
                    wordOffset + 1,
                    rank1Array,
                    LBSInBoolArrayPreprocess
                )
            }
            currentIndex++
            charIndex++
        }
        return -1
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

    private tailrec fun searchShortArray(
        index: Int,
        chars: CharArray,
        wordOffset: Int,
        rank1Array: ShortArray,
        LBSInBoolArrayPreprocess: IntArray
    ): Int {
        var currentIndex = index
        var charIndex = LBS.rank1CommonShort(currentIndex, rank1Array)
        val charCount = chars.size

        while (currentIndex < LBS.size() && LBS[currentIndex]) {
            val currentChar = chars[wordOffset]
            val currentLabel = labels[charIndex.toInt()]

            if (currentChar == currentLabel) {
                if (wordOffset + 1 == charCount) {
                    return if (isLeaf[currentIndex]) currentIndex else currentIndex
                }
                val nextIndex = indexOfLabel(charIndex.toInt(), LBSInBoolArrayPreprocess)
                return searchShortArray(
                    nextIndex,
                    chars,
                    wordOffset + 1,
                    rank1Array,
                    LBSInBoolArrayPreprocess
                )
            }
            currentIndex++
            charIndex++
        }
        return -1
    }

    fun writeExternalNotCompress(out: ObjectOutput) {
        try {
            out.apply {
                writeObject(LBS)
                writeObject(isLeaf)
                writeObject(labelsTemp.toCharArray())
                writeObject(termIds.toIntArray())
                flush()
                close()
            }
        } catch (e: IOException) {
            println(e.stackTraceToString())
        }
    }

    fun readExternalNotCompress(objectInput: ObjectInput): LOUDSWithTermId {
        objectInput.apply {
            try {
                LBS = objectInput.readObject() as BitSet
                isLeaf = objectInput.readObject() as BitSet
                labels = (objectInput.readObject() as CharArray)
                termIdsSaved = (objectInput.readObject() as IntArray)
                close()
            } catch (e: Exception) {
                println(e.stackTraceToString())
            }
        }
        return LOUDSWithTermId(LBS, labels, isLeaf, termIdsSaved)
    }

}
