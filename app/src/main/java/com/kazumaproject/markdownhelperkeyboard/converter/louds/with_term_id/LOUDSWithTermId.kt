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
    var termIdsDiff: ShortArray = shortArrayOf()
    var isLeaf: BitSet = BitSet()
    val isLeafTemp: MutableList<Boolean> = arrayListOf()

    init {
        LBSTemp.apply {
            add(true)
            add(false)
        }
        labelsTemp.apply {
            add(0,' ')
            add(1,' ')
        }
        isLeafTemp.apply {
            add(0,false)
            add(1,false)
        }
    }

    constructor()

    constructor(
        LBS: BitSet,
        labels: CharArray,
        isLeaf: BitSet,
        termIdsList: ShortArray,
    ){
        this.LBS = LBS
        this.labels = labels
        this.isLeaf = isLeaf
        this.termIdsDiff = termIdsList
    }

    fun convertListToBitSet(){
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
        while (parentNodeIndex != 0){
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
        while (parentNodeIndex != 0){
            val parentNodeId = LBS.rank1(parentNodeIndex)
            val pair = labels[parentNodeId]
            list.add(pair)
            parentNodeIndex = LBS.select1(LBS.rank0(parentNodeIndex))
        }
        return list.toList().reversed().joinToString("")
    }

    fun getNodeIndex(s: String, rank1Array: IntArray, LBSInBoolArray: BooleanArray): Int{
        return search(2, s.toCharArray(), 0, rank1Array,LBSInBoolArray)
    }

    fun getNodeIndex(s: String, rank1Array: ShortArray, LBSInBoolArray: BooleanArray): Int{
        return searchShortArray(2, s.toCharArray(), 0, rank1Array,LBSInBoolArray)
    }

    fun getTermId(
        nodeIndex: Int,
        rank1Array: IntArray
    ): Int {
        val firstNodeId: Int = isLeaf.rank1Common(nodeIndex,rank1Array) - 1
        if (firstNodeId < 0) return -1
        val firstTermId: Int = if (termIdsDiff[firstNodeId].toInt() == 0){
            firstNodeId + 1
        }else{
            firstNodeId + termIdsDiff[firstNodeId]
        }
        return firstTermId
    }

    fun getTermIdShortArray(
        nodeIndex: Int,
        rank1Array: ShortArray
    ): Short {
        val firstNodeId: Int = isLeaf.rank1CommonShort(nodeIndex,rank1Array) - 1
        if (firstNodeId < 0) return -1
        val firstTermId: Int = if (termIdsDiff[firstNodeId].toInt() == 0){
            firstNodeId + 1
        }else{
            firstNodeId + termIdsDiff[firstNodeId]
        }
        return firstTermId.toShort()
    }

    private fun firstChild(
        pos: Int,
        rank0Array: IntArray,
        rank1Array: IntArray
    ): Int {
        val y = LBS.select0Common(LBS.rank1Common(pos,rank1Array),rank0Array) + 1
        return if (!LBS[y]) -1 else y
    }

    private fun firstChildShortArray(
        pos: Int,
        rank0Array: ShortArray,
        rank1Array: ShortArray
    ): Int {
        val y = LBS.select0CommonShort(LBS.rank1CommonShort(pos,rank1Array),rank0Array) + 1
        return if (!LBS[y]) -1 else y
    }

    private fun traverse(
        pos: Int,
        c: Char,
        rank0Array: IntArray,
        rank1Array: IntArray
    ): Int {
        var childPos = firstChild(pos,rank0Array, rank1Array)
        if (childPos < 0) return -1
        while (LBS[childPos]){
            if (c == labels[LBS.rank1Common(childPos,rank1Array)]) {
                return childPos
            }
            childPos += 1
        }
        return -1
    }

    private fun traverseShortArray(
        pos: Int,
        c: Char,
        rank0Array: ShortArray,
        rank1Array: ShortArray
    ): Int {
        var childPos = firstChildShortArray(pos,rank0Array, rank1Array)
        if (childPos < 0) return -1
        while (LBS[childPos]){
            if (c == labels[LBS.rank1CommonShort(childPos,rank1Array).toInt()]) {
                return childPos
            }
            childPos += 1
        }
        return -1
    }

    fun commonPrefixSearch(
        str: String,
        rank0Array: IntArray,
        rank1Array: IntArray,
    ): List<String> {
        val resultTemp = StringBuilder()
        val result: MutableList<String> = mutableListOf()
        var n = 0
        for (c in str) {
            n = traverse(n, c, rank0Array, rank1Array)
            val index = LBS.rank1Common(n, rank1Array)
            println("$c $n $index")
            if (n < 0 || index >= labels.size) break

            resultTemp.append(labels[index])
            if (isLeaf[n]) {
                result.add(resultTemp.toString())
            }
        }
        println("common prefix result: $result")
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
            println("$c $n $index")
            if (n < 0 || index >= labels.size) break

            resultTemp.append(labels[index.toInt()])
            if (isLeaf[n]) {
                result.add(resultTemp.toString())
            }
        }
        println("common prefix result: $result")
        return result
    }

    private tailrec fun search(
        index: Int,
        chars: CharArray,
        wordOffset: Int,
        rank1Array: IntArray,
        LBSInBoolArray: BooleanArray
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
                val nextIndex = indexOfLabel(charIndex, LBSInBoolArray)
                return search(nextIndex, chars, wordOffset + 1, rank1Array, LBSInBoolArray)
            }
            currentIndex++
            charIndex++
        }
        return -1
    }

    private tailrec fun searchShortArray(
        index: Int,
        chars: CharArray,
        wordOffset: Int,
        rank1Array: ShortArray,
        LBSInBoolArray: BooleanArray
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
                val nextIndex = indexOfLabel(charIndex.toInt(), LBSInBoolArray)
                return searchShortArray(nextIndex, chars, wordOffset + 1, rank1Array, LBSInBoolArray)
            }
            currentIndex++
            charIndex++
        }
        return -1
    }

    private fun indexOfLabel(label: Int, LBSInBoolArray: BooleanArray): Int {
        var count = 0
        var i = 0
        val size = LBSInBoolArray.size

        while (i < size) {
            if (!LBSInBoolArray[i]) {
                if (++count == label) {
                    return i + 1
                }
            }
            i++
        }
        return size
    }

    fun writeExternal(out: ObjectOutput){
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
        }catch (e: IOException){
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
                labels = (objectInput.readObject() as ByteArray).inflate(labelsSize).toListChar().toCharArray()
                termIds = (objectInput.readObject() as ByteArray).inflate(termIdSize).toListInt().toMutableList()
                it.close()
            }catch (e: Exception){
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
                termIdsDiff = (objectInput.readObject() as ShortArray)
                close()
            }catch (e: Exception){
                println(e.stackTraceToString())
            }
        }
        return LOUDSWithTermId(LBS, labels, isLeaf, termIdsDiff)
    }

}