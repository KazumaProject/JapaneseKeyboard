package com.kazumaproject.Louds.with_term_id

import com.kazumaproject.bitset.rank0
import com.kazumaproject.bitset.rank1
import com.kazumaproject.bitset.select0
import com.kazumaproject.bitset.select1
import com.kazumaproject.connection_id.deflate
import com.kazumaproject.connection_id.inflate
import com.kazumaproject.toBitSet
import com.kazumaproject.toBooleanList
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
    var nodeIds: MutableList<Int> = arrayListOf()
    var labels: MutableList<Char> = arrayListOf()
    var termIds: MutableList<Int> = arrayListOf()
    var isLeaf: BitSet = BitSet()
    val isLeafTemp: MutableList<Boolean> = arrayListOf()

    init {
        LBSTemp.apply {
            add(true)
            add(false)
        }
        nodeIds.apply {
            add(0,0)
            add(1,1)
        }
        labels.apply {
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
        nodeIds: MutableList<Int>,
        labels: MutableList<Char>,
        isLeaf: BitSet,
        termIds: MutableList<Int>,
    ){
        this.LBS = LBS
        this.nodeIds = nodeIds
        this.labels = labels
        this.isLeaf = isLeaf
        this.termIds = termIds
    }

    fun getNodeIdSize(): Int = nodeIds.size

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

    fun getNodeIndex(s: String): Int{
        return search(2, s.toCharArray(), 0)
    }

    fun getNodeId(s: String): Int{
        return LBS.rank0(getNodeIndex(s))
    }

    fun getTermId(nodeIndex: Int): Int {
        val firstNodeId = isLeaf.rank1(nodeIndex) - 1
        if (firstNodeId < 0) return -1
        val firstTermId = termIds[firstNodeId]
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
        while (LBS[childPos]){
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
            if (isLeaf[n]){
                val tempStr = resultTemp.joinToString("")
                if (result.size >= 1){
                    val resultStr = result[0] + tempStr
                    result.add(resultStr)
                }else {
                    result.add(tempStr)
                    resultTemp.clear()
                }
            }
        }
        return result.toList()
    }

    fun contain(str: String): Boolean{
        return searchForContain(2,str.toCharArray(),0) == SearchStatus.LEAF_FOUND
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


    fun writeExternal(out: ObjectOutput){
        try {
            out.apply {
                writeObject(nodeIds.toByteArray().size)
                writeObject(labels.toByteArrayFromListChar().size)
                writeObject(termIds.toByteArray().size)

                writeObject(LBS)
                writeObject(nodeIds.toByteArray().deflate())
                writeObject(labels.toByteArrayFromListChar().deflate())
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
                val nodeIdSize = it.readObject() as Int
                val labelsSize = it.readObject() as Int
                val termIdSize = it.readObject() as Int
                LBS = it.readObject() as BitSet
                nodeIds = (it.readObject() as ByteArray).inflate(nodeIdSize).toListInt()
                labels = (it.readObject() as ByteArray).inflate(labelsSize).toListChar()
                isLeaf = it.readObject() as BitSet
                termIds = (it.readObject() as ByteArray).inflate(termIdSize).toListInt()
            }catch (e: Exception){
                println(e.stackTraceToString())
            }
        }
        return LOUDSWithTermId(LBS, nodeIds, labels, isLeaf, termIds)
    }

    private fun searchForContain(index: Int, chars: CharArray, wordOffset: Int): SearchStatus {
        var index2 = index
        var wordOffset2 = wordOffset
        var charIndex = countTrue(index)
        while (LBS[index]) {
            if (chars[wordOffset] == labels[charIndex]) {
                if (isLeaf[index] && wordOffset + 1 == chars.size) return SearchStatus.LEAF_FOUND
                else if (wordOffset + 1 == chars.size) return SearchStatus.PART_CONTAINS
                return searchForContain(indexOfLabel(charIndex), chars, ++wordOffset2)
            } else {
                index2++
            }
            charIndex++
        }
        return SearchStatus.NOT_FOUND
    }

    private fun countTrue(to: Int): Int {
        return LBS.toBooleanList().subList(0, to + 1).stream().filter { elm -> elm }.count().toInt()
    }

    enum class SearchStatus {
        LEAF_FOUND,
        PART_CONTAINS,
        NOT_FOUND
    }

}