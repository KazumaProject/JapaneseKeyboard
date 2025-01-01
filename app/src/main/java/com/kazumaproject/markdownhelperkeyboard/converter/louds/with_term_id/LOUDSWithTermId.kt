package com.kazumaproject.Louds.with_term_id

import com.kazumaproject.bitset.rank0
import com.kazumaproject.bitset.rank1
import com.kazumaproject.bitset.rank1Common
import com.kazumaproject.bitset.rank1CommonShort
import com.kazumaproject.bitset.select0Common
import com.kazumaproject.bitset.select0CommonShort
import com.kazumaproject.bitset.select1
import com.kazumaproject.connection_id.deflate
import com.kazumaproject.connection_id.inflate
import com.kazumaproject.toBitSet
import com.kazumaproject.toByteArray
import com.kazumaproject.toByteArrayFromListChar
import com.kazumaproject.toListChar
import com.kazumaproject.toListInt
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
        LBSInBoolArray: BooleanArray,
        LBSInBoolArrayPreprocess: IntArray
    ): Int {
        return search(2, s.toCharArray(), 0, rank1Array, LBSInBoolArray, LBSInBoolArrayPreprocess)
    }

    fun getNodeIndex(
        s: String,
        rank1Array: ShortArray,
        LBSInBoolArray: BooleanArray,
        LBSInBoolArrayPreprocess: IntArray
    ): Int {
        return searchShortArray(
            2,
            s.toCharArray(),
            0,
            rank1Array,
            LBSInBoolArray,
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

    fun getTermIdShortArray(
        nodeIndex: Int,
        rank1Array: ShortArray
    ): Short {
        val firstNodeId: Int = isLeaf.rank1CommonShort(nodeIndex, rank1Array) - 1
        if (firstNodeId < 0) return -1
        val firstTermId: Int = termIdsSaved[firstNodeId]
        return firstTermId.toShort()
    }


    private fun firstChildShortArray(
        pos: Int,
        rank0Array: ShortArray,
        rank1Array: ShortArray
    ): Int {
        val y = LBS.select0CommonShort(LBS.rank1CommonShort(pos, rank1Array), rank0Array) + 1
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

    private fun select0CommonCached(pos: Int, rank0Array: IntArray): Int {
        val cachedResults = mutableMapOf<Pair<Int, IntArray>, Int>()
        return cachedResults.getOrPut(Pair(pos, rank0Array)) {
            LBS.select0Common(pos, rank0Array)
        }
    }

    private fun firstChild(pos: Int, rank0Array: IntArray, rank1Array: IntArray): Int {
        val y = select0CommonCached(LBS.rank1Common(pos, rank1Array), rank0Array) + 1
        return if (y < 0 || !LBS[y]) -1 else y
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
            //println("$c $n $index")
            if (n < 0 || index >= labels.size) break

            resultTemp.append(labels[index.toInt()])
            if (isLeaf[n]) {
                result.add(resultTemp.toString())
            }
        }
        //println("common prefix result: $result")
        return result
    }

    private tailrec fun search(
        index: Int,
        chars: CharArray,
        wordOffset: Int,
        rank1Array: IntArray,
        LBSInBoolArray: BooleanArray,
        LBSInBoolArrayPreprocess: IntArray
    ): Int {
        var currentIndex = index
        var charIndex = LBS.rank1Common(currentIndex, rank1Array)
        val charCount = chars.size

        while (currentIndex < LBSInBoolArray.size && LBSInBoolArray[currentIndex]) {
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
                    LBSInBoolArray,
                    LBSInBoolArrayPreprocess
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
        LBSInBoolArray: BooleanArray,
        LBSInBoolArrayPreprocess: IntArray
    ): Int {
        var currentIndex = index
        var charIndex = LBS.rank1CommonShort(currentIndex, rank1Array)
        val charCount = chars.size

        while (currentIndex < LBSInBoolArray.size && LBSInBoolArray[currentIndex]) {
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
                    LBSInBoolArray,
                    LBSInBoolArrayPreprocess
                )
            }
            currentIndex++
            charIndex++
        }
        return -1
    }

    fun writeExternal(out: ObjectOutput) {
        try {
            out.apply {
                writeInt(labels.toList().toByteArrayFromListChar().size)
                writeInt(termIds.toByteArray().size)

                writeObject(LBS)
                writeObject(labels.toList().toByteArrayFromListChar().deflate())
                writeObject(isLeaf)
                writeObject(termIds.toByteArray().deflate())
                flush()
                close()
            }
        } catch (e: IOException) {
            println(e.stackTraceToString())
        }
    }

    fun readExternal(objectInput: ObjectInput): LOUDSWithTermId {
        objectInput.use {
            try {
                val labelsSize = objectInput.readInt()
                val termIdSize = objectInput.readInt()
                LBS = objectInput.readObject() as BitSet
                isLeaf = objectInput.readObject() as BitSet
                labels = (objectInput.readObject() as ByteArray).inflate(labelsSize).toListChar()
                    .toCharArray()
                termIds = (objectInput.readObject() as ByteArray).inflate(termIdSize).toListInt()
                    .toMutableList()
                it.close()
            } catch (e: Exception) {
                println(e.stackTraceToString())
            }
        }
        return LOUDSWithTermId()
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
