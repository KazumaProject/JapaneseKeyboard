package com.kazumaproject.Louds.with_term_id

import com.kazumaproject.bitset.rank0
import com.kazumaproject.bitset.rank1
import com.kazumaproject.bitset.select0
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
    var labels: MutableList<Char> = arrayListOf()
    var termIds: MutableList<Int> = arrayListOf()
    var termIdsList: IntArray = intArrayOf()
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
        termIdsList: IntArray,
    ){
        this.LBS = LBS
        this.labels = labels
        this.isLeaf = isLeaf
        this.termIdsList = termIdsList
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

    fun getNodeIndex(s: String): Int{
        return search(2, s.toCharArray(), 0)
    }

    fun getNodeId(s: String): Int{
        return LBS.rank0(getNodeIndex(s))
    }

    fun getTermId(nodeIndex: Int): Int {
        val firstNodeId = isLeaf.rank1(nodeIndex) - 1
        if (firstNodeId < 0) return -1
        return termIdsList[firstNodeId]
    }

    private fun firstChild(pos: Int): Int {
        val y = LBS.select0(LBS.rank1(pos)) + 1
        return if (!LBS[y]) -1 else y
    }

    private fun traverse(pos: Int, c: Char): Int {
        var childPos = firstChild(pos)
        if (childPos < 0) return -1
        while (LBS[childPos]){
            if (c == labels[LBS.rank1(childPos)]) {
                return childPos
            }
            childPos += 1
        }
        return -1
    }

       fun commonPrefixSearch(str: String): List<String>  {
        val resultTemp: MutableList<Char> = mutableListOf()
        val result: MutableList<String> = mutableListOf()
        var n = 0
          for (c in str){
              n = traverse(n, c)
              val index = LBS.rank1(n)
              if (n < 0) break
              if (index >= labels.size) break
              resultTemp.add(labels[index])
              if (isLeaf[n]){
                  val tempStr = resultTemp.joinToString("")
                  result.add(tempStr)
              }
          }
         return result.toList()
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
                writeInt(termIds.toByteArray().size)

                writeObject(LBS)
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
                val labelsSize = objectInput.readInt()
                val termIdSize = objectInput.readInt()
                LBS = objectInput.readObject() as BitSet
                isLeaf = objectInput.readObject() as BitSet
                labels = (objectInput.readObject() as ByteArray).inflate(labelsSize).toListChar()
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
                labels = (objectInput.readObject() as CharArray).toMutableList()
                termIdsList = (objectInput.readObject() as IntArray)
                close()
            }catch (e: Exception){
                println(e.stackTraceToString())
            }
        }
        return LOUDSWithTermId(LBS, labels, isLeaf, termIdsList)
    }

}