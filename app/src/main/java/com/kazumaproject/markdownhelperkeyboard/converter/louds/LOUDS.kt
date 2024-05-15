package com.kazumaproject.Louds

import com.kazumaproject.bitset.rank0
import com.kazumaproject.bitset.rank1
import com.kazumaproject.bitset.select0
import com.kazumaproject.bitset.select1
import com.kazumaproject.connection_id.deflate
import com.kazumaproject.connection_id.inflate
import com.kazumaproject.toByteArrayFromListChar
import com.kazumaproject.toListChar
import java.io.IOException
import java.io.ObjectInput
import java.io.ObjectOutput
import java.util.BitSet
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class LOUDS {
    val LBSTemp: MutableList<Boolean> = arrayListOf()
    var LBS: BitSet = BitSet()
    var labels: MutableList<Char> = arrayListOf()
    var isLeaf: BitSet = BitSet()
    val isLeafTemp: MutableList<Boolean> = arrayListOf()

    init {
        LBSTemp.apply {
            add(true)
            add(false)
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
        labels: MutableList<Char>,
        isLeaf: BitSet,
    ){
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
        while (LBS[childPos]){
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
        return result
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
                writeInt(labels.toByteArrayFromListChar().size)
                writeObject(LBS)
                writeObject(labels.toByteArrayFromListChar().deflate())
                writeObject(isLeaf)
                flush()
                close()
            }
        }catch (e: IOException){
            println(e.stackTraceToString())
        }
    }

    @OptIn(ExperimentalTime::class)
    fun readExternal(objectInput: ObjectInput): LOUDS {
        objectInput.use {
            try {
                val time = measureTime {
                    val labelSize = it.readInt()
                    LBS = it.readObject() as BitSet
                    labels = (it.readObject() as ByteArray).inflate(labelSize).toListChar()
                    isLeaf = it.readObject() as BitSet
                }
                println("loading time tango.dat: $time")
            }catch (e: Exception){
                println(e.stackTraceToString())
            }
        }
        return LOUDS(LBS, labels, isLeaf)
    }


}