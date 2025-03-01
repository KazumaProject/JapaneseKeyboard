package com.kazumaproject.Louds

import com.kazumaproject.bitset.rank0
import com.kazumaproject.bitset.rank0Common
import com.kazumaproject.bitset.rank0CommonShort
import com.kazumaproject.bitset.rank1
import com.kazumaproject.bitset.rank1Common
import com.kazumaproject.bitset.rank1CommonShort
import com.kazumaproject.bitset.select0
import com.kazumaproject.bitset.select1
import com.kazumaproject.bitset.select1Common
import com.kazumaproject.bitset.select1CommonShort
import com.kazumaproject.connection_id.deflate
import com.kazumaproject.connection_id.inflate
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.toByteArrayFromListChar
import com.kazumaproject.toListChar
import java.io.IOException
import java.io.ObjectInput
import java.io.ObjectOutput
import java.util.BitSet

class LOUDS {
    val LBSTemp: MutableList<Boolean> = arrayListOf()
    var LBS: BitSet = BitSet()
    var labels: CharArray = charArrayOf()
    val labelsTemp: MutableList<Char> = arrayListOf()
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
    ) {
        this.LBS = LBS
        this.labels = labels
        this.isLeaf = isLeaf
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

    fun commonPrefixSearch(str: String): MutableList<String> {
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
        return result
    }

    fun getAllLabels(): CharArray {
        return labels
    }

    fun getLetter(
        nodeIndex: Int,
        rank0Array: IntArray,
        rank1Array: IntArray
    ): String {
        val result = StringBuilder()
        var currentNodeIndex = nodeIndex

        while (true) {
            val currentNodeId = LBS.rank1Common(currentNodeIndex, rank1Array)
            val currentChar = labels[currentNodeId]

            /** Remove this for Wakati **/
            if (currentChar != ' ') {
                result.append(currentChar)
            }

            if (currentNodeId == 0) break

            currentNodeIndex =
                LBS.select1Common(LBS.rank0Common(currentNodeIndex, rank0Array), rank1Array)
        }
        return result.reverse().toString()
    }

    fun getLetter(
        nodeIndex: Int,
        succinctBitVector: SuccinctBitVector
    ): String {
        val result = StringBuilder()
        var currentNodeIndex = nodeIndex

        while (true) {
            val currentNodeId = succinctBitVector.rank1(currentNodeIndex)
            val currentChar = labels[currentNodeId]

            /** Remove this for Wakati **/
            if (currentChar != ' ') {
                result.append(currentChar)
            }

            if (currentNodeId == 0) break
            val rank0 = succinctBitVector.rank0(currentNodeIndex)
            currentNodeIndex = succinctBitVector.select1(rank0)
        }
        return result.reverse().toString()
    }

    fun getLetterShortArray(
        nodeIndex: Int,
        rank0Array: ShortArray,
        rank1Array: ShortArray,
    ): String {
        val list = mutableListOf<Char>()
        val firstNodeId = LBS.rank1CommonShort(nodeIndex, rank1Array)
        val firstChar = labels[firstNodeId.toInt()]
        list.add(firstChar)
        var parentNodeIndex = LBS.select1CommonShort(
            LBS.rank0CommonShort(nodeIndex.toShort(), rank0Array),
            rank1Array
        ).toInt()
        while (parentNodeIndex != 0) {
            val parentNodeId = LBS.rank1CommonShort(parentNodeIndex, rank1Array)
            val pair = labels[parentNodeId.toInt()]
            list.add(pair)
            parentNodeIndex = LBS.select1CommonShort(
                LBS.rank0CommonShort(parentNodeIndex.toShort(), rank0Array),
                rank1Array
            ).toInt()
            if (parentNodeId == (0).toShort()) return ""
        }
        return list.toList().asReversed().joinToString("")
    }

    fun getLetterShortArray(
        nodeIndex: Int,
        succinctBitVector: SuccinctBitVector
    ): String {
        val list = mutableListOf<Char>()
        val firstNodeId = succinctBitVector.rank1(nodeIndex)
        val firstChar = labels[firstNodeId]
        list.add(firstChar)
        val rank0 = succinctBitVector.rank0(nodeIndex)
        var parentNodeIndex = succinctBitVector.select1(rank0)
        while (parentNodeIndex != 0) {
            val parentNodeId = succinctBitVector.rank1(parentNodeIndex)
            val pair = labels[parentNodeId]
            list.add(pair)
            val rank0InLoop = succinctBitVector.rank0(parentNodeIndex)
            parentNodeIndex = succinctBitVector.select1(rank0InLoop)
            if (parentNodeId == (0)) return ""
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

    fun getNodeIndex(s: String): Int {
        return search(2, s.toCharArray(), 0)
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
                writeInt(labelsTemp.toByteArrayFromListChar().size)
                writeObject(LBS)
                writeObject(labelsTemp.toByteArrayFromListChar().deflate())
                writeObject(isLeaf)
                flush()
                close()
            }
        } catch (e: IOException) {
            println(e.stackTraceToString())
        }
    }

    fun readExternal(objectInput: ObjectInput): LOUDS {
        objectInput.use {
            try {
                val labelSize = it.readInt()
                LBS = it.readObject() as BitSet
                labels =
                    (it.readObject() as ByteArray).inflate(labelSize).toListChar().toCharArray()
                isLeaf = it.readObject() as BitSet
            } catch (e: Exception) {
                println(e.stackTraceToString())
            }
        }
        return LOUDS(LBS, labels, isLeaf)
    }

    fun readExternalNotCompress(objectInput: ObjectInput): LOUDS {
        objectInput.apply {
            try {
                LBS = objectInput.readObject() as BitSet
                isLeaf = objectInput.readObject() as BitSet
                labels = (objectInput.readObject() as CharArray)
                close()
            } catch (e: Exception) {
                println(e.stackTraceToString())
            }
        }
        return LOUDS(LBS, labels, isLeaf)
    }

}
