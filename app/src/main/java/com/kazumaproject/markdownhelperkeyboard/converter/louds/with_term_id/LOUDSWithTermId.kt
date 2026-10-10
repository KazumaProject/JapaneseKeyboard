package com.kazumaproject.Louds.with_term_id

import com.kazumaproject.bitset.rank0
import com.kazumaproject.bitset.rank1
import com.kazumaproject.bitset.rank1Common
import com.kazumaproject.bitset.rank1CommonShort
import com.kazumaproject.bitset.select0Common
import com.kazumaproject.bitset.select0CommonShort
import com.kazumaproject.bitset.select1
import com.kazumaproject.core.domain.flick.FlickInputEvidence
import com.kazumaproject.connection_id.deflate
import com.kazumaproject.connection_id.inflate
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.compact.PackedCharArray
import com.kazumaproject.markdownhelperkeyboard.converter.compact.PackedIntArray
import com.kazumaproject.markdownhelperkeyboard.converter.graph.FlickCorrectionEdit
import com.kazumaproject.markdownhelperkeyboard.converter.graph.FlickCorrectionInput
import com.kazumaproject.markdownhelperkeyboard.converter.graph.FlickCorrectionKind
import com.kazumaproject.markdownhelperkeyboard.converter.graph.FlickDir
import com.kazumaproject.markdownhelperkeyboard.converter.graph.KanaFlickLayout
import com.kazumaproject.markdownhelperkeyboard.converter.graph.OmissionSearchResult
import com.kazumaproject.markdownhelperkeyboard.converter.graph.TypoCandidate
import com.kazumaproject.markdownhelperkeyboard.converter.graph.TypoCategory
import com.kazumaproject.markdownhelperkeyboard.converter.graph.TypoCorrectionResult
import com.kazumaproject.toBitSet
import com.kazumaproject.toByteArray
import com.kazumaproject.toByteArrayFromListChar
import com.kazumaproject.toListChar
import com.kazumaproject.toListInt
import java.io.IOException
import java.io.ObjectInput
import java.io.ObjectOutput
import java.util.BitSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLongArray


class LOUDSWithTermId {

    data class CommonPrefixSearchResult(
        val yomi: String,
        val nodeIndex: Int,
    )

    data class CommonPrefixSearchProgress(
        val results: List<CommonPrefixSearchResult>,
        val nodeIndex: Int,
    )

    data class OmissionSearchState(
        val nodeIndex: Int,
        val omissionOccurred: Boolean,
        val yomi: String = "",
    )

    data class OmissionSearchProgress(
        val results: List<OmissionSearchResult>,
        val terminalStates: List<OmissionSearchState>,
    )

    data class TypoSearchState(
        val nodeIndex: Int,
        val penaltyUsed: Int,
        val depth: Int,
        val yomi: String = "",
    )

    data class TypoSearchProgress(
        val results: List<TypoCorrectionResult>,
        val terminalStates: List<TypoSearchState>,
        val acceptedPenalties: Map<String, Int>,
        val flickProgress: FlickSearchProgress? = null,
    )

    val LBSTemp: MutableList<Boolean> = arrayListOf()
    var LBS: BitSet = BitSet()
    var labels: CharArray = charArrayOf()
    private var packedLabels: PackedCharArray? = null
    val labelsTemp: MutableList<Char> = arrayListOf()
    var termIds: MutableList<Int> = arrayListOf()
    var termIdsSaved: IntArray = intArrayOf()
    private var packedTermIds: PackedIntArray? = null
    var isLeaf: BitSet = BitSet()
    val isLeafTemp: MutableList<Boolean> = arrayListOf()
    private val typoCandidateCache = ConcurrentHashMap<Char, List<TypoCandidate>>(64)
    private class FlickChildCache(val bits: BitSet, val vector: SuccinctBitVector) {
        val entries = AtomicLongArray(4096)
    }
    @Volatile private var flickChildCache: FlickChildCache? = null

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
        installLabels(labels)
        this.isLeaf = isLeaf
        installTermIds(termIdsList)
    }

    private val labelCount: Int
        get() = packedLabels?.size ?: labels.size

    private val termIdCount: Int
        get() = packedTermIds?.size ?: termIdsSaved.size

    private fun labelAt(index: Int): Char = packedLabels?.get(index) ?: labels[index]

    private fun allLabels(): CharArray = packedLabels?.toCharArray() ?: labels

    private fun installLabels(values: CharArray) {
        if (values.size >= PACKED_ARRAY_THRESHOLD) {
            packedLabels = PackedCharArray.from(values)
            labels = charArrayOf()
        } else {
            packedLabels = null
            labels = values
        }
    }

    private fun termIdAt(index: Int): Int = packedTermIds?.get(index) ?: termIdsSaved[index]

    private fun allTermIds(): IntArray = packedTermIds?.toIntArray() ?: termIdsSaved

    fun getAllLabels(): CharArray = allLabels()

    fun getAllTermIds(): IntArray = allTermIds()

    private fun installTermIds(values: IntArray) {
        if (values.size >= PACKED_ARRAY_THRESHOLD) {
            packedTermIds = PackedIntArray.from(values)
            termIdsSaved = intArrayOf()
        } else {
            packedTermIds = null
            termIdsSaved = values
        }
    }

    fun convertListToBitSet() {
        LBS = LBSTemp.toBitSet()
        LBSTemp.clear()
        isLeaf = isLeafTemp.toBitSet()
        isLeafTemp.clear()
        labels = labelsTemp.toCharArray()
        labelsTemp.clear()
    }

    fun getLetter(nodeIndex: Int): String {
        val list = mutableListOf<Char>()
        val firstNodeId = LBS.rank1(nodeIndex)
        val firstChar = labelAt(firstNodeId)
        list.add(firstChar)
        var parentNodeIndex = LBS.select1(LBS.rank0(nodeIndex))
        while (parentNodeIndex != 0) {
            val parentNodeId = LBS.rank1(parentNodeIndex)
            val pair = labelAt(parentNodeId)
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
            val pair = labelAt(parentNodeId)
            list.add(pair)
            parentNodeIndex = LBS.select1(LBS.rank0(parentNodeIndex))
        }
        return list.toList().asReversed().joinToString("")
    }

    fun contains(
        str: String, rank1Array: IntArray, rank0Array: IntArray
    ): Boolean {
        var currentNodeIndex = 0

        for (char in str) {
            currentNodeIndex = traverse(
                currentNodeIndex, char, rank0Array = rank0Array, rank1Array = rank1Array
            )
            if (currentNodeIndex == -1) return false
        }
        return isLeaf[currentNodeIndex]
    }


    fun getNodeIndex(
        s: String, rank1Array: IntArray, LBSInBoolArrayPreprocess: IntArray
    ): Int {
        if (s.isEmpty()) return -1
        return search(2, s.toCharArray(), 0, rank1Array, LBSInBoolArrayPreprocess)
    }

    fun getNodeIndex(
        s: String,
        succinctBitVector: SuccinctBitVector,
    ): Int {
        if (s.isEmpty()) return -1
        return search(
            2,
            s.toCharArray(),
            0,
            succinctBitVector,
        )
    }

    fun getNodeIndex(
        s: String, rank1Array: ShortArray, LBSInBoolArrayPreprocess: IntArray
    ): Int {
        if (s.isEmpty()) return -1
        return searchShortArray(
            2, s.toCharArray(), 0, rank1Array, LBSInBoolArrayPreprocess
        )
    }

    fun getTermId(
        nodeIndex: Int, rank1Array: IntArray
    ): Int {
        if (!isTerminalNodeIndex(nodeIndex)) return -1
        val firstNodeId: Int = isLeaf.rank1Common(nodeIndex, rank1Array) - 1
        if (firstNodeId !in 0 until termIdCount) return -1
        return termIdAt(firstNodeId)
    }

    fun getTermId(
        nodeIndex: Int, succinctBitVector: SuccinctBitVector
    ): Int {
        if (!isTerminalNodeIndex(nodeIndex)) return -1
        val firstNodeId: Int = succinctBitVector.rank1(nodeIndex) - 1
        if (firstNodeId !in 0 until termIdCount) return -1
        return termIdAt(firstNodeId)
    }

    fun getTermIdShortArray(
        nodeIndex: Int, rank1Array: ShortArray
    ): Short {
        if (!isTerminalNodeIndex(nodeIndex)) return -1
        val firstNodeId: Int = isLeaf.rank1CommonShort(nodeIndex, rank1Array) - 1
        if (firstNodeId !in 0 until termIdCount) return -1
        val firstTermId: Int = termIdAt(firstNodeId)
        return firstTermId.toShort()
    }

    fun getTermIdShortArray(
        nodeIndex: Int, succinctBitVector: SuccinctBitVector
    ): Short {
        if (!isTerminalNodeIndex(nodeIndex)) return -1
        val firstNodeId: Int = succinctBitVector.rank1(nodeIndex) - 1
        if (firstNodeId !in 0 until termIdCount) return -1
        val firstTermId: Int = termIdAt(firstNodeId)
        return firstTermId.toShort()
    }

    private fun isTerminalNodeIndex(nodeIndex: Int): Boolean =
        nodeIndex in 0 until LBS.size() && isLeaf[nodeIndex]


    private fun firstChildShortArray(
        pos: Int, rank0Array: ShortArray, rank1Array: ShortArray
    ): Int {
        val rank1Value = LBS.rank1CommonShort(pos, rank1Array)
        val select0 = LBS.select0CommonShort(rank1Value, rank0Array)
        val y = select0 + 1
        return if (!LBS[y]) -1 else y
    }

    private fun traverseShortArray(
        pos: Int, c: Char, rank0Array: ShortArray, rank1Array: ShortArray
    ): Int {
        var childPos = firstChildShortArray(pos, rank0Array, rank1Array)
        if (childPos < 0) return -1
        while (LBS[childPos]) {
            val labelIndex = LBS.rank1CommonShort(childPos, rank1Array).toInt()
            if (labelIndex !in 0 until labelCount) return -1
            if (c == labelAt(labelIndex)) {
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
            val labelIndex = LBS.rank1Common(childPos, rank1Array)
            if (labelIndex !in 0 until labelCount) return -1
            if (c == labelAt(labelIndex)) {
                return childPos
            }
            childPos++
        }
        return -1
    }

    private fun traverse(pos: Int, c: Char, succinctBitVector: SuccinctBitVector): Int {
        var childPos = firstChild(pos, succinctBitVector)
        while (childPos >= 0 && LBS[childPos]) {
            val labelIndex = succinctBitVector.rank1(childPos)
            if (labelIndex !in 0 until labelCount) return -1
            if (c == labelAt(labelIndex)) {
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
            if (index >= labelCount) break
            resultTemp.append(labelAt(index))
            if (isLeaf[n]) {
                result.add(resultTemp.toString())
            }
        }
        return result
    }

    fun commonPrefixSearch(str: String, succinctBitVector: SuccinctBitVector): List<String> {
        return commonPrefixSearchWithNodeIndex(str, 0, succinctBitVector).map { it.yomi }
    }

    fun commonPrefixSearchWithNodeIndex(
        str: CharSequence,
        start: Int,
        succinctBitVector: SuccinctBitVector,
    ): List<CommonPrefixSearchResult> = commonPrefixSearchWithProgress(
        str = str,
        start = start,
        succinctBitVector = succinctBitVector,
    ).results

    fun commonPrefixSearchWithProgress(
        str: CharSequence,
        start: Int,
        succinctBitVector: SuccinctBitVector,
    ): CommonPrefixSearchProgress {
        val resultWithNodeIndex = mutableListOf<CommonPrefixSearchResult>()
        val resultTemp = StringBuilder()
        var n = 0
        for (indexInString in start until str.length) {
            val c = str[indexInString]
            n = traverse(n, c, succinctBitVector)
            if (n < 0) break
            val index = succinctBitVector.rank1(n)
            if (index >= labelCount) break
            resultTemp.append(labelAt(index))
            if (isLeaf[n]) {
                val yomi = resultTemp.toString()
                resultWithNodeIndex.add(CommonPrefixSearchResult(yomi, n))
            }
        }
        return CommonPrefixSearchProgress(resultWithNodeIndex, n)
    }

    fun advanceCommonPrefixSearch(
        previousNodeIndex: Int,
        char: Char,
        input: CharSequence,
        start: Int,
        succinctBitVector: SuccinctBitVector,
    ): CommonPrefixSearchProgress {
        if (previousNodeIndex < 0) {
            return CommonPrefixSearchProgress(emptyList(), -1)
        }
        val nextNodeIndex = traverse(previousNodeIndex, char, succinctBitVector)
        if (nextNodeIndex < 0) {
            return CommonPrefixSearchProgress(emptyList(), -1)
        }
        val result = if (isLeaf[nextNodeIndex]) {
            listOf(
                CommonPrefixSearchResult(
                    yomi = input.subSequence(start, input.length).toString(),
                    nodeIndex = nextNodeIndex,
                )
            )
        } else {
            emptyList()
        }
        return CommonPrefixSearchProgress(result, nextNodeIndex)
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
            if (index >= labelCount) break
            prefix.append(labelAt(index))
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
            if (index >= labelCount) break
            prefix.append(labelAt(index.toInt()))
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
            if (index >= labelCount) break
            prefix.append(labelAt(index))
            collectWords(childPos, prefix, succinctBitVector, result)
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
            if (index >= labelCount) return result
            resultTemp.append(labelAt(index))
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
            if (index >= labelCount) return result
            resultTemp.append(labelAt(index))
        }
        // Collect all words starting from the last matched node
        collectWords(n, resultTemp, succinctBitVector, result)
        return result
    }

    fun predictiveSearch(
        prefix: String, rank0Array: ShortArray, rank1Array: ShortArray
    ): List<String> {
        val result = mutableListOf<String>()
        val resultTemp = StringBuilder()
        var n = 0
        for (c in prefix) {
            n = traverseShortArray(n, c, rank0Array, rank1Array)
            if (n < 0) return result // No match found
            val index = LBS.rank1CommonShort(n, rank1Array)
            if (index >= labelCount) return result
            resultTemp.append(labelAt(index.toInt()))
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
            if (n < 0 || index >= labelCount) break

            resultTemp.append(labelAt(index.toInt()))
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
            if (charIndex !in 0 until labelCount) return -1
            val currentChar = chars[wordOffset]
            val currentLabel = labelAt(charIndex)

            if (currentChar == currentLabel) {
                if (wordOffset + 1 == charCount) {
                    return if (isLeaf[currentIndex]) currentIndex else -1
                }
                val nextIndex = indexOfLabel(charIndex, LBSInBoolArrayPreprocess)
                return search(
                    nextIndex, chars, wordOffset + 1, rank1Array, LBSInBoolArrayPreprocess
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
            if (charIndex !in 0 until labelCount) return -1
            val currentChar = chars[wordOffset]
            val currentLabel = labelAt(charIndex)

            if (currentChar == currentLabel) {
                if (wordOffset + 1 == charCount) {
                    return if (isLeaf[currentIndex]) currentIndex else -1
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
            if (charIndex.toInt() !in 0 until labelCount) return -1
            val currentChar = chars[wordOffset]
            val currentLabel = labelAt(charIndex.toInt())

            if (currentChar == currentLabel) {
                if (wordOffset + 1 == charCount) {
                    return if (isLeaf[currentIndex]) currentIndex else -1
                }
                val nextIndex = indexOfLabel(charIndex.toInt(), LBSInBoolArrayPreprocess)
                return searchShortArray(
                    nextIndex, chars, wordOffset + 1, rank1Array, LBSInBoolArrayPreprocess
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
                writeInt(allLabels().toList().toByteArrayFromListChar().size)
                writeInt(termIds.toByteArray().size)

                writeObject(LBS)
                writeObject(allLabels().toList().toByteArrayFromListChar().deflate())
                writeObject(isLeaf)
                writeObject(termIds.toByteArray().deflate())
                flush()
                close()
            }
        } catch (e: IOException) {
            println(e.stackTraceToString())
        }
    }

    fun writeExternalNotCompress(out: ObjectOutput) {
        try {
            out.apply {
                writeObject(LBS)
                writeObject(isLeaf)
                writeObject(allLabels())
                writeObject(if (termIds.isNotEmpty()) termIds.toIntArray() else allTermIds())
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

    /**
     * 修飾キー省略を考慮した共通接頭辞検索を行います。
     *
     * @return OmissionSearchResultのリスト。省略が発生したかのフラグを含む。
     */
    fun commonPrefixSearchWithOmission(
        str: String, succinctBitVector: SuccinctBitVector
    ): List<OmissionSearchResult> = commonPrefixSearchWithOmissionProgress(
        str = str,
        startIndex = 0,
        succinctBitVector = succinctBitVector,
    ).results

    /**
     * 省略検索の候補と、入力末尾での探索状態を同時に返す。
     * 探索状態は1文字追加時に [advanceOmissionSearch] へ渡せる。
     */
    fun commonPrefixSearchWithOmissionProgress(
        str: CharSequence,
        startIndex: Int,
        succinctBitVector: SuccinctBitVector,
    ): OmissionSearchProgress {
        if (startIndex !in str.indices) {
            return OmissionSearchProgress(emptyList(), emptyList())
        }
        var states = listOf(OmissionSearchState(nodeIndex = 0, omissionOccurred = false))
        val results = linkedSetOf<OmissionSearchResult>()
        for (index in startIndex until str.length) {
            states = advanceOmissionStates(states, str[index], succinctBitVector)
            if (states.isEmpty()) break
            states.forEach { state ->
                if (isLeaf[state.nodeIndex]) {
                    results.add(
                        OmissionSearchResult(
                            yomi = state.yomi,
                            omissionOccurred = state.omissionOccurred,
                        )
                    )
                }
            }
        }
        return OmissionSearchProgress(results.toList(), states)
    }

    /** 保持した省略探索状態を1文字だけ進める。 */
    fun advanceOmissionSearch(
        states: List<OmissionSearchState>,
        char: Char,
        succinctBitVector: SuccinctBitVector,
    ): OmissionSearchProgress {
        val advanced = advanceOmissionStates(states, char, succinctBitVector)
        val results = advanced.mapNotNullTo(ArrayList()) { state ->
            if (!isLeaf[state.nodeIndex]) {
                null
            } else {
                OmissionSearchResult(
                    yomi = state.yomi,
                    omissionOccurred = state.omissionOccurred,
                )
            }
        }
        return OmissionSearchProgress(results, advanced)
    }

    private fun advanceOmissionStates(
        states: List<OmissionSearchState>,
        char: Char,
        succinctBitVector: SuccinctBitVector,
    ): List<OmissionSearchState> {
        if (states.isEmpty()) return emptyList()
        val advanced = ArrayList<OmissionSearchState>(states.size * 2)
        states.forEach { state ->
            forEachCharVariation(char) { variant ->
                val child = traverse(state.nodeIndex, variant, succinctBitVector)
                if (child >= 0) {
                    advanced.add(
                        OmissionSearchState(
                            nodeIndex = child,
                            omissionOccurred = state.omissionOccurred || variant != char,
                            yomi = state.yomi + variant,
                        )
                    )
                }
            }
        }
        return advanced
    }

    /**
     * 文字のバリエーションを返すヘルパー関数。
     * （濁点、半濁音、小文字など）
     *
     * @param char 変換元の文字
     * @return 変換後の文字のリスト
     */
    private inline fun forEachCharVariation(char: Char, block: (Char) -> Unit) {
        block(char)
        when (char) {
            'か' -> block('が')
            'き' -> block('ぎ')
            'く' -> block('ぐ')
            'け' -> block('げ')
            'こ' -> block('ご')
            'さ' -> block('ざ')
            'し' -> block('じ')
            'す' -> block('ず')
            'せ' -> block('ぜ')
            'そ' -> block('ぞ')
            'た' -> block('だ')
            'ち' -> block('ぢ')
            'つ' -> {
                block('づ')
                block('っ')
            }
            'て' -> block('で')
            'と' -> block('ど')
            'は' -> {
                block('ば')
                block('ぱ')
            }
            'ひ' -> {
                block('び')
                block('ぴ')
            }
            'ふ' -> {
                block('ぶ')
                block('ぷ')
            }
            'へ' -> {
                block('べ')
                block('ぺ')
            }
            'ほ' -> {
                block('ぼ')
                block('ぽ')
            }
            'や' -> block('ゃ')
            'ゆ' -> block('ゅ')
            'よ' -> block('ょ')
            'あ' -> block('ぁ')
            'い' -> block('ぃ')
            'う' -> block('ぅ')
            'え' -> block('ぇ')
            'お' -> block('ぉ')
        }
    }

    internal data class FlickSearchState(
        val nodeIndex: Int,
        val depth: Int,
        val edits: List<FlickCorrectionEdit> = emptyList(),
        val costUnits: Int = 0,
        val modifierOmission: Boolean = false,
    )

    class FlickSearchProgress internal constructor(
        internal val input: String,
        internal val startIndex: Int,
        internal val terminalStates: List<FlickSearchState>,
        internal val swapPrefixStates: List<FlickSearchState>,
        internal val accepted: List<TypoCorrectionResult>,
        internal val allowModifierOmission: Boolean,
        internal val maxStates: Int,
        internal val evidence: List<FlickInputEvidence?>,
        internal val leadingStates: List<FlickSearchState> = emptyList(),
    )

    private data class FlickSubstitution(val edit: FlickCorrectionEdit, val modifierOmission: Boolean) {
        val singleEdit = listOf(edit)
    }

    /** Bounded weighted edit search; input offsets and trie depth deliberately differ. */
    fun commonPrefixSearchWithFlickCorrection(
        str: String,
        startIndex: Int,
        succinctBitVector: SuccinctBitVector,
        correctionInput: FlickCorrectionInput,
        allowModifierOmission: Boolean,
        maxLen: Int = 12,
        maxStates: Int = 256,
        maxResults: Int = 32,
        cancellationCheck: () -> Unit = {},
    ): List<TypoCorrectionResult> = commonPrefixSearchWithFlickCorrectionProgress(str, startIndex,
        succinctBitVector, correctionInput, allowModifierOmission, maxLen, maxStates, maxResults,
        cancellationCheck = cancellationCheck).results

    fun commonPrefixSearchWithFlickCorrectionProgress(
        str: String,
        startIndex: Int,
        succinctBitVector: SuccinctBitVector,
        correctionInput: FlickCorrectionInput,
        allowModifierOmission: Boolean,
        maxLen: Int = 12,
        maxStates: Int = 256,
        maxResults: Int = 32,
        previous: FlickSearchProgress? = null,
        cancellationCheck: () -> Unit = {},
        resultScore: ((TypoCorrectionResult) -> Int)? = null,
    ): TypoSearchProgress {
        fun emptyProgress() = TypoSearchProgress(emptyList(), emptyList(), emptyMap())
        if (startIndex !in str.indices || maxStates <= 0 || maxResults <= 0) return emptyProgress()
        var limit = 0
        while (limit < maxLen && startIndex + limit < str.length &&
            isFlickKana(str[startIndex + limit])) limit++
        if (limit < 3) return emptyProgress()
        val input = str.substring(startIndex, startIndex + limit)
        // The sixth character opens the two-substitution search. Rebuild once at that
        // boundary; shorter requests need not retain paths they cannot return yet.
        val reusable = previous?.takeIf { it.startIndex == startIndex && input.startsWith(it.input) &&
            it.allowModifierOmission == allowModifierOmission && it.maxStates == maxStates &&
            (it.input.length >= 6 || limit < 6) &&
            it.evidence == correctionInput.evidence.drop(startIndex).take(it.input.length) }
        val slots = Array(limit + 1) { ArrayList<FlickSearchState>() }
        if (reusable == null) slots[0].add(FlickSearchState(0, 0))
        else slots[reusable.input.length].addAll(reusable.terminalStates)
        val accepted = LinkedHashMap<Pair<Int, Int>, TypoCorrectionResult>()
        reusable?.accepted?.forEach { accepted[it.nodeIndex to it.consumedLength] = it }
        val firstUsed = reusable?.input?.length ?: 0
        var swapPrefixStates = reusable?.swapPrefixStates.orEmpty()
        accepted.entries.removeAll { it.value.consumedLength >= firstUsed }
        val order = Comparator<FlickSearchState> { first, second ->
            var comparison = first.edits.size.compareTo(second.edits.size)
            if (comparison == 0) comparison = first.costUnits.compareTo(second.costUnits)
            if (comparison == 0) comparison = first.modifierOmission.compareTo(second.modifierOmission)
            if (comparison == 0) comparison = first.nodeIndex.compareTo(second.nodeIndex)
            comparison
        }
        fun stateKey(state: FlickSearchState): Long = (state.nodeIndex.toLong() shl 3) or
            (state.edits.size.toLong() shl 1) or if (state.modifierOmission) 1L else 0L
        fun bounded(states: List<FlickSearchState>): List<FlickSearchState> {
            val seen = HashSet<Long>(states.size)
            val ordered = states.sortedWith(order).filter { seen.add(stateKey(it)) }
            if (ordered.size <= maxStates) return ordered
            // Cheaper direction errors must not crowd out every missing/extra/swap path.
            val groups = ordered.filter { it.edits.size <= 1 }.groupBy { state ->
                val first = state.edits.firstOrNull()
                ((first?.kind?.ordinal ?: -1) + 1) * 8 + state.edits.size * 2 +
                    if (first?.kind == FlickCorrectionKind.MISSING && first.inputStart == startIndex) 1 else 0
            }
            val reserved = groups.values.flatMap { it.take((maxStates / (groups.size + 2)).coerceAtLeast(1)) }
            seen.clear()
            val retained = ArrayList<FlickSearchState>(maxStates)
            for (state in reserved) if (seen.add(stateKey(state))) retained.add(state)
            for (state in ordered) {
                if (retained.size >= maxStates) break
                if (seen.add(stateKey(state))) retained.add(state)
            }
            return retained.sortedWith(order)
        }
        fun add(slot: Int, state: FlickSearchState) {
            if (state.costUnits > 5000 || state.depth > maxLen + 1) return
            slots[slot].add(state)
            if (slots[slot].size >= maxStates * 2) {
                val retained = bounded(slots[slot])
                slots[slot].clear()
                slots[slot].addAll(retained)
            }
        }
        fun edit(state: FlickSearchState, kind: FlickCorrectionKind, from: Int, end: Int,
                 replacement: String, units: Int, supported: Boolean = false): List<FlickCorrectionEdit> =
            state.edits + FlickCorrectionEdit(kind, startIndex + from, startIndex + end,
                replacement, units, supported)
        fun variants(char: Char): List<Char> = if (allowModifierOmission) buildList {
            add(char)
            forEachCharVariation(char) { if (it != char) add(it) }
        } else listOf(char)
        // A missing first character has no exact prefix to anchor it. Keep its small
        // exact suffix frontier separately so cheap substitutions cannot erase it.
        // This frontier also survives appends, without looking ahead or changing a
        // previously pruned beam based on characters that have not been typed yet.
        var leadingStates = reusable?.leadingStates ?: buildList {
            forEachFlickChild(0, succinctBitVector) { node, first ->
                if (isFlickKana(first)) add(FlickSearchState(node, 1,
                    listOf(FlickCorrectionEdit(FlickCorrectionKind.MISSING, startIndex,
                        startIndex, first.toString(), 1200)), 1200))
            }
        }
        for (used in firstUsed..limit) {
            cancellationCheck()
            for (state in leadingStates) if (used >= 3 && isLeaf[state.nodeIndex]) {
                val result = TypoCorrectionResult(flickReading(state.nodeIndex, state.depth, succinctBitVector),
                    1, state.nodeIndex, used, state.edits, state.modifierOmission, state.costUnits)
                accepted[state.nodeIndex to used] = result
            }
            if (used == limit || leadingStates.isEmpty()) break
            val character = str[startIndex + used]
            val next = ArrayList<FlickSearchState>()
            val seen = HashSet<Int>()
            for (state in leadingStates) for (alternative in variants(character)) {
                val node = traverseFlick(state.nodeIndex, alternative, succinctBitVector)
                if (node >= 0 && seen.add(node)) next.add(state.copy(nodeIndex = node,
                    depth = state.depth + 1, modifierOmission = state.modifierOmission || alternative != character))
            }
            leadingStates = next.take(maxStates.coerceAtMost(128))
        }
        // A transposition can skip the previous last-character frontier. Retain its exact
        // prefixes separately so an append only processes the new character and this edge.
        if (reusable != null && firstUsed < limit) {
            val firstChar = str[startIndex + firstUsed - 1]
            val secondChar = str[startIndex + firstUsed]
            if (firstChar != secondChar) for (state in swapPrefixStates) {
                for (second in variants(secondChar)) {
                    val firstNode = traverseFlick(state.nodeIndex, second, succinctBitVector)
                    if (firstNode < 0) continue
                    for (first in variants(firstChar)) {
                        val secondNode = traverseFlick(firstNode, first, succinctBitVector)
                        if (secondNode < 0) continue
                        add(firstUsed + 1, FlickSearchState(secondNode, state.depth + 2,
                            edit(state, FlickCorrectionKind.TRANSPOSE, firstUsed - 1, firstUsed + 1,
                                "" + second + first, 1600), 1600,
                            state.modifierOmission || first != firstChar || second != secondChar))
                    }
                }
            }
        }
        for (used in firstUsed..limit) {
            cancellationCheck()
            val states = bounded(slots[used])
            val expanded = ArrayList(states)
            val char = str.getOrNull(startIndex + used) ?: '\u0000'
            val exactVariants = variants(char)
            val fromKey = KanaFlickLayout.keyOf(KanaFlickLayout.baseChar(char))
            val substitutions = HashMap<Char, FlickSubstitution?>()
            // Epsilon insertion is allowed once. It stays attached to a positive-length word.
            for (state in states) {
                if (state.edits.isNotEmpty()) continue
                forEachFlickChild(state.nodeIndex, succinctBitVector) { node, char ->
                    if (isFlickKana(char)) {
                        val units = 1200
                        expanded.add(FlickSearchState(node, state.depth + 1,
                            edit(state, FlickCorrectionKind.MISSING, used, used, char.toString(), units),
                            units, state.modifierOmission))
                    }
                }
            }
            val processedStates = bounded(expanded)
            if (used == limit - 1) swapPrefixStates = processedStates.filter { it.edits.isEmpty() }
            for (state in processedStates) {
                if (used >= 3 && state.edits.isNotEmpty() && isLeaf[state.nodeIndex] &&
                    (state.edits.size == 1 || used >= 6)) {
                    val result = TypoCorrectionResult(flickReading(state.nodeIndex, state.depth, succinctBitVector), state.edits.size, state.nodeIndex,
                        used, state.edits, state.modifierOmission, state.costUnits)
                    val key = state.nodeIndex to used
                    if (accepted[key]?.costUnits?.let { it <= state.costUnits } != true) accepted[key] = result
                }
                if (used == limit) continue
                val canSubstitute = state.edits.isEmpty() || (limit >= 6 && state.edits.size == 1 &&
                    state.edits.first().kind in SUBSTITUTION_KINDS)
                // An exhausted edit budget can only follow this exact character. Avoid
                // inspecting every sibling, preserving the same state and insertion order.
                if (!canSubstitute && !allowModifierOmission) {
                    val node = traverseFlick(state.nodeIndex, char, succinctBitVector)
                    if (node >= 0) add(used + 1, state.copy(nodeIndex = node, depth = state.depth + 1))
                    continue
                }
                forEachFlickChild(state.nodeIndex, succinctBitVector) { node, candidate ->
                    if (candidate in exactVariants) {
                        add(used + 1, state.copy(nodeIndex = node, depth = state.depth + 1,
                            modifierOmission = state.modifierOmission || candidate != char))
                    } else if (canSubstitute && isFlickKana(candidate) &&
                        (allowModifierOmission || KanaFlickLayout.modifierOf(char) == KanaFlickLayout.modifierOf(candidate))) {
                        // Geometry and evidence are constant for this input position; reuse
                        // the edit across trie states instead of recalculating every branch.
                        val substitution = substitutions.getOrPut(candidate) {
                            val toKey = KanaFlickLayout.keyOf(KanaFlickLayout.baseChar(candidate))
                            if (fromKey == null || toKey == null || fromKey == toKey) return@getOrPut null
                            val sameGroup = fromKey.group == toKey.group
                            val sameDirection = fromKey.dir == toKey.dir
                            val distance = KanaFlickLayout.manhattan(fromKey.group, toKey.group)
                            val kind = when {
                                sameGroup -> FlickCorrectionKind.DIRECTION
                                sameDirection -> FlickCorrectionKind.KEY
                                else -> FlickCorrectionKind.KEY_AND_DIRECTION
                            }
                            val base = when {
                                sameGroup && (fromKey.dir == FlickDir.CENTER || toKey.dir == FlickDir.CENTER) -> 900
                                sameGroup && ((fromKey.dir == FlickDir.LEFT && toKey.dir == FlickDir.RIGHT) ||
                                    (fromKey.dir == FlickDir.RIGHT && toKey.dir == FlickDir.LEFT) ||
                                    (fromKey.dir == FlickDir.UP && toKey.dir == FlickDir.DOWN) ||
                                    (fromKey.dir == FlickDir.DOWN && toKey.dir == FlickDir.UP)) -> 1500
                                sameGroup -> 1100
                                sameDirection && distance == 1 -> 1100
                                sameDirection && distance == 2 -> 2400
                                sameDirection -> 4000
                                distance == 1 -> 2300
                                else -> 4600
                            }
                            val factor = correctionInput.evidenceAt(startIndex + used)
                                ?.costFactor(KanaFlickLayout.baseChar(candidate)) ?: 1f
                            val units = (base * factor).toInt()
                            FlickSubstitution(FlickCorrectionEdit(kind, startIndex + used, startIndex + used + 1,
                                candidate.toString(), units, factor < 0.85f),
                                KanaFlickLayout.modifierOf(char) != KanaFlickLayout.modifierOf(candidate))
                        }
                        if (substitution != null && state.costUnits + substitution.edit.costUnits <= 5000)
                            add(used + 1, FlickSearchState(node, state.depth + 1,
                            if (state.edits.isEmpty()) substitution.singleEdit else state.edits + substitution.edit,
                            state.costUnits + substitution.edit.costUnits, state.modifierOmission || substitution.modifierOmission))
                    }
                }
                if (state.edits.isNotEmpty()) continue
                val repeated = str.getOrNull(startIndex + used - 1) == char ||
                    str.getOrNull(startIndex + used + 1) == char
                val deletionUnits = if (repeated) 1000 else 2400
                add(used + 1, state.copy(
                    edits = edit(state, FlickCorrectionKind.EXTRA, used, used + 1, "", deletionUnits),
                    costUnits = deletionUnits,
                ))
                if (used + 1 < limit && char != str[startIndex + used + 1]) {
                    for (second in variants(str[startIndex + used + 1])) {
                        val firstNode = traverseFlick(state.nodeIndex, second, succinctBitVector)
                        if (firstNode < 0) continue
                        for (first in exactVariants) {
                            val secondNode = traverseFlick(firstNode, first, succinctBitVector)
                            if (secondNode < 0) continue
                            add(used + 2, FlickSearchState(secondNode, state.depth + 2,
                                edit(state, FlickCorrectionKind.TRANSPOSE, used, used + 2, "" + second + first, 1600),
                                1600, state.modifierOmission || first != char || second != str[startIndex + used + 1]))
                        }
                    }
                }
            }
        }
        val weightedResults = accepted.values.map { result ->
            val last = result.edits.singleOrNull()?.takeIf { it.kind == FlickCorrectionKind.MISSING &&
                it.inputStart == str.length && result.consumedLength == limit }
            if (last == null) result else result.copy(edits = listOf(last.copy(costUnits = 2800)),
                costUnits = result.costUnits + 2800 - last.costUnits)
        }
        val scores = resultScore?.let { score -> weightedResults.associate { result ->
            (result.nodeIndex to result.consumedLength) to score(result)
        } }
        val resultOrder = compareBy<TypoCorrectionResult> {
            scores?.get(it.nodeIndex to it.consumedLength) ?: it.costUnits
        }
            .thenByDescending { it.consumedLength }.thenBy { it.yomi }
        val ordered = weightedResults.sortedWith(resultOrder)
        val results = if (ordered.size <= maxResults) ordered else {
            val reserved = ordered.filter { it.consumedLength == limit }
                .groupBy { Triple(it.edits.first().kind, it.edits.size,
                    it.edits.first().kind == FlickCorrectionKind.MISSING && it.edits.first().inputStart == startIndex) }
                .values.flatMap { it.take(2) }
            (reserved + ordered).distinctBy { it.nodeIndex to it.consumedLength }.take(maxResults).sortedWith(resultOrder)
        }
        return TypoSearchProgress(results, emptyList(), emptyMap(), FlickSearchProgress(input, startIndex,
            slots[limit].toList(), swapPrefixStates, accepted.values.toList(), allowModifierOmission, maxStates,
            correctionInput.evidence.drop(startIndex).take(limit), leadingStates))
    }

    private inline fun forEachFlickChild(node: Int, vector: SuccinctBitVector, block: (Int, Char) -> Unit) {
        var child = firstFlickChild(node, vector)
        var labelIndex = if (child >= 0) vector.rank1(child) else -1
        while (child >= 0 && LBS[child]) {
            if (labelIndex !in 0 until labelCount) break
            block(child, labelAt(labelIndex))
            child++
            labelIndex++
        }
    }

    private fun firstFlickChild(node: Int, vector: SuccinctBitVector): Int {
        val cache = flickChildCache?.takeIf { it.bits === LBS && it.vector === vector } ?: synchronized(this) {
            flickChildCache?.takeIf { it.bits === LBS && it.vector === vector } ?: FlickChildCache(LBS, vector)
                .also { flickChildCache = it }
        }
        val slot = (node * -1640531527) and 4095
        val saved = cache.entries.get(slot)
        if (saved ushr 32 == node.toLong() + 1) return saved.toInt() - 1
        val first = firstChild(node, vector)
        // The key and value share one atomic cell, including a cached missing child.
        cache.entries.set(slot, ((node.toLong() + 1) shl 32) or ((first.toLong() + 1) and 0xffffffffL))
        return first
    }

    private fun traverseFlick(node: Int, char: Char, vector: SuccinctBitVector): Int {
        var result = -1
        forEachFlickChild(node, vector) { child, label -> if (label == char) result = child }
        return result
    }

    private fun flickReading(node: Int, depth: Int, vector: SuccinctBitVector): String {
        val reading = CharArray(depth)
        var current = node
        for (index in depth - 1 downTo 0) {
            reading[index] = labelAt(vector.rank1(current))
            current = vector.select1(vector.rank0(current))
        }
        return String(reading)
    }

    private fun isFlickKana(char: Char): Boolean = char in 'ぁ'..'ゖ' || char == 'ー'

    fun commonPrefixSearchWithTypoCorrectionPrefix(
        str: String,
        succinctBitVector: SuccinctBitVector,
        maxPenalty: Int = 2,
        maxLen: Int = 12,
        maxResults: Int = 64, // 任意: 暴発防止
    ): List<TypoCorrectionResult> = commonPrefixSearchWithTypoCorrectionProgress(
        str = str,
        startIndex = 0,
        succinctBitVector = succinctBitVector,
        maxPenalty = maxPenalty,
        maxLen = maxLen,
        maxResults = maxResults,
    ).results

    fun commonPrefixSearchWithTypoCorrectionProgress(
        str: CharSequence,
        startIndex: Int,
        succinctBitVector: SuccinctBitVector,
        maxPenalty: Int = 2,
        maxLen: Int = 12,
        maxResults: Int = 64,
    ): TypoSearchProgress {
        if (startIndex !in str.indices) {
            return TypoSearchProgress(emptyList(), emptyList(), emptyMap())
        }

        // Use the exact same character-at-a-time transition as an incremental append. The old
        // cold path used depth-first traversal while append used frontier traversal; once the
        // bounded result set reached maxResults, they retained different typo paths and could
        // produce different first candidates for the same input.
        var progress = TypoSearchProgress(
            results = emptyList(),
            terminalStates = listOf(TypoSearchState(0, 0, 0, "")),
            acceptedPenalties = emptyMap(),
        )
        val acceptedResults = LinkedHashMap<String, Int>()
        val acceptedNodeIndices = HashMap<String, Int>()
        val endExclusive = minOf(str.length, startIndex + maxLen)
        for (index in startIndex until endExclusive) {
            progress = advanceTypoCorrectionSearch(
                previous = progress,
                char = str[index],
                succinctBitVector = succinctBitVector,
                maxPenalty = maxPenalty,
                maxLen = maxLen,
                maxResults = maxResults,
            )
            progress.results.forEach { result ->
                val previousPenalty = acceptedResults[result.yomi]
                if (previousPenalty == null || result.penaltyUsed < previousPenalty) {
                    acceptedResults[result.yomi] = result.penaltyUsed
                    acceptedNodeIndices[result.yomi] = result.nodeIndex
                }
            }
            if (progress.terminalStates.isEmpty()) break
        }

        val results = acceptedResults.entries
            .sortedWith(
                compareBy<Map.Entry<String, Int>> { it.value }
                    .thenByDescending { it.key.length }
                    .thenBy { it.key }
            )
            .map {
                TypoCorrectionResult(
                    yomi = it.key,
                    penaltyUsed = it.value,
                    nodeIndex = checkNotNull(acceptedNodeIndices[it.key]),
                )
            }
        return progress.copy(results = results)
    }

    fun advanceTypoCorrectionSearch(
        previous: TypoSearchProgress,
        char: Char,
        succinctBitVector: SuccinctBitVector,
        maxPenalty: Int = 2,
        maxLen: Int = 12,
        maxResults: Int = 64,
    ): TypoSearchProgress {
        if (previous.acceptedPenalties.size >= maxResults || previous.terminalStates.isEmpty()) {
            return TypoSearchProgress(emptyList(), emptyList(), previous.acceptedPenalties)
        }
        val bestPenaltyByYomi = HashMap(previous.acceptedPenalties)
        val terminalStates = ArrayList<TypoSearchState>()
        val newResults = LinkedHashMap<String, Int>()
        val newResultNodeIndices = HashMap<String, Int>()
        val typoCandidates = getTypoCandidates(char)
        for (state in previous.terminalStates) {
            if (bestPenaltyByYomi.size >= maxResults) break
            if (state.depth >= maxLen) continue
            for (candidate in typoCandidates) {
                val nextPenalty = state.penaltyUsed + candidate.penalty
                if (nextPenalty > maxPenalty) continue
                val child = traverse(state.nodeIndex, candidate.ch, succinctBitVector)
                if (child < 0) continue
                val yomi = state.yomi + candidate.ch
                terminalStates.add(TypoSearchState(child, nextPenalty, state.depth + 1, yomi))
                if (!isLeaf[child]) continue
                val oldPenalty = bestPenaltyByYomi[yomi]
                if (oldPenalty == null || nextPenalty < oldPenalty) {
                    bestPenaltyByYomi[yomi] = nextPenalty
                    newResults[yomi] = nextPenalty
                    newResultNodeIndices[yomi] = child
                }
            }
        }
        val results = newResults.entries
            .sortedWith(
                compareBy<Map.Entry<String, Int>> { it.value }
                    .thenByDescending { it.key.length }
                    .thenBy { it.key }
            )
            .map {
                TypoCorrectionResult(
                    yomi = it.key,
                    penaltyUsed = it.value,
                    nodeIndex = checkNotNull(newResultNodeIndices[it.key]),
                )
            }
        return TypoSearchProgress(results, terminalStates, bestPenaltyByYomi)
    }

    private fun getTypoCandidates(ch: Char): List<TypoCandidate> {
        typoCandidateCache[ch]?.let { return it }
        val key = KanaFlickLayout.keyOf(ch) ?: return listOf(
            TypoCandidate(ch, TypoCategory.Exact)
        ).also { typoCandidateCache[ch] = it }

        val out = ArrayList<TypoCandidate>(16)

        // 1) Exact
        out.add(TypoCandidate(ch, TypoCategory.Exact))

        // 2) TapKeyInFlick: 同じgroup内の別dir
        for (dir in FlickDir.entries) {
            if (dir == key.dir) continue
            val v = KanaFlickLayout.charOf(key.group, dir) ?: continue
            out.add(TypoCandidate(v, TypoCategory.TapKeyInFlick))
        }

        // 3) DistanceNear/Middle/Far: 同dirのまま別groupへ
        for (g in KanaFlickLayout.allGroups()) {
            if (g == key.group) continue
            val v = KanaFlickLayout.charOf(g, key.dir) ?: continue

            val dist = KanaFlickLayout.manhattan(key.group, g)
            val cat = when (dist) {
                1 -> TypoCategory.DistanceNear
                2 -> TypoCategory.DistanceMiddle
                else -> TypoCategory.DistanceFar
            }
            out.add(TypoCandidate(v, cat))
        }

        // 重複排除 + penalty昇順（探索枝刈りに効く）
        return out
            .distinctBy { it.ch to it.category } // 同じ文字が複数カテゴリに入る設計にしたいならここは要調整
            .sortedWith(compareBy<TypoCandidate> { it.penalty }.thenBy { it.ch })
            .also { typoCandidateCache[ch] = it }
    }

    companion object {
        private val SUBSTITUTION_KINDS = setOf(FlickCorrectionKind.KEY,
            FlickCorrectionKind.DIRECTION, FlickCorrectionKind.KEY_AND_DIRECTION)
        private const val PACKED_ARRAY_THRESHOLD = 500_000

        fun fromPacked(
            LBS: BitSet,
            labels: PackedCharArray,
            isLeaf: BitSet,
            termIds: PackedIntArray,
        ): LOUDSWithTermId = LOUDSWithTermId().apply {
            this.LBS = LBS
            this.labels = charArrayOf()
            this.packedLabels = labels
            this.isLeaf = isLeaf
            this.termIds = arrayListOf()
            this.termIdsSaved = intArrayOf()
            this.packedTermIds = termIds
        }
    }
}
