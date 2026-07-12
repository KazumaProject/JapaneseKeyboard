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
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
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


class LOUDSWithTermId {

    data class CommonPrefixSearchResult(
        val yomi: String,
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
    )

    val LBSTemp: MutableList<Boolean> = arrayListOf()
    var LBS: BitSet = BitSet()
    var labels: CharArray = charArrayOf()
    val labelsTemp: MutableList<Char> = arrayListOf()
    var termIds: MutableList<Int> = arrayListOf()
    var termIdsSaved: IntArray = intArrayOf()
    var isLeaf: BitSet = BitSet()
    val isLeafTemp: MutableList<Boolean> = arrayListOf()
    private val typoCandidateCache = ConcurrentHashMap<Char, List<TypoCandidate>>(64)

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
        labels = labelsTemp.toCharArray()
        labelsTemp.clear()
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
        s: String, rank1Array: ShortArray, LBSInBoolArrayPreprocess: IntArray
    ): Int {
        return searchShortArray(
            2, s.toCharArray(), 0, rank1Array, LBSInBoolArrayPreprocess
        )
    }

    fun getTermId(
        nodeIndex: Int, rank1Array: IntArray
    ): Int {
        val firstNodeId: Int = isLeaf.rank1Common(nodeIndex, rank1Array) - 1
        if (firstNodeId < 0) return -1
        return termIdsSaved[firstNodeId]
    }

    fun getTermId(
        nodeIndex: Int, succinctBitVector: SuccinctBitVector
    ): Int {
        val firstNodeId: Int = succinctBitVector.rank1(nodeIndex) - 1
        if (firstNodeId < 0) return -1
        return termIdsSaved[firstNodeId]
    }

    fun getTermIdShortArray(
        nodeIndex: Int, rank1Array: ShortArray
    ): Short {
        val firstNodeId: Int = isLeaf.rank1CommonShort(nodeIndex, rank1Array) - 1
        if (firstNodeId < 0) return -1
        val firstTermId: Int = termIdsSaved[firstNodeId]
        return firstTermId.toShort()
    }

    fun getTermIdShortArray(
        nodeIndex: Int, succinctBitVector: SuccinctBitVector
    ): Short {
        val firstNodeId: Int = succinctBitVector.rank1(nodeIndex) - 1
        if (firstNodeId < 0) return -1
        val firstTermId: Int = termIdsSaved[firstNodeId]
        return firstTermId.toShort()
    }


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
            if (labelIndex !in labels.indices) return -1
            if (c == labels[labelIndex]) {
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
            if (labelIndex !in labels.indices) return -1
            if (c == labels[labelIndex]) {
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
            if (labelIndex !in labels.indices) return -1
            if (c == labels[labelIndex]) {
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
        return commonPrefixSearchWithNodeIndex(str, 0, succinctBitVector).map { it.yomi }
    }

    fun commonPrefixSearchWithNodeIndex(
        str: CharSequence,
        start: Int,
        succinctBitVector: SuccinctBitVector,
    ): List<CommonPrefixSearchResult> {
        val resultWithNodeIndex = mutableListOf<CommonPrefixSearchResult>()
        val resultTemp = StringBuilder()
        var n = 0
        for (indexInString in start until str.length) {
            val c = str[indexInString]
            n = traverse(n, c, succinctBitVector)
            if (n < 0) break
            val index = succinctBitVector.rank1(n)
            if (index >= labels.size) break
            resultTemp.append(labels[index])
            if (isLeaf[n]) {
                val yomi = resultTemp.toString()
                resultWithNodeIndex.add(CommonPrefixSearchResult(yomi, n))
            }
        }
        return resultWithNodeIndex
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
        prefix: String, rank0Array: ShortArray, rank1Array: ShortArray
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
            if (charIndex !in labels.indices) return -1
            val currentChar = chars[wordOffset]
            val currentLabel = labels[charIndex]

            if (currentChar == currentLabel) {
                if (wordOffset + 1 == charCount) {
                    return if (isLeaf[currentIndex]) currentIndex else currentIndex
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
            if (charIndex !in labels.indices) return -1
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
            if (charIndex.toInt() !in labels.indices) return -1
            val currentChar = chars[wordOffset]
            val currentLabel = labels[charIndex.toInt()]

            if (currentChar == currentLabel) {
                if (wordOffset + 1 == charCount) {
                    return if (isLeaf[currentIndex]) currentIndex else currentIndex
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

    fun writeExternalNotCompress(out: ObjectOutput) {
        try {
            out.apply {
                writeObject(LBS)
                writeObject(isLeaf)
                writeObject(labels)
                writeObject(termIds.toIntArray())
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

        // 同一yomiの重複を最小penaltyで集約
        val bestPenaltyByYomi = HashMap<String, Int>(128)
        val sb = StringBuilder(maxLen)
        val terminalStates = ArrayList<TypoSearchState>()

        fun acceptIfLeaf(nodeIndex: Int, penaltyUsed: Int) {
            if (sb.isEmpty()) return
            if (!isLeaf[nodeIndex]) return

            val yomi = sb.toString()
            val prev = bestPenaltyByYomi[yomi]
            if (prev == null || penaltyUsed < prev) {
                bestPenaltyByYomi[yomi] = penaltyUsed
            }
        }

        fun dfs(strIndex: Int, nodeIndex: Int, penaltyUsed: Int) {
            if (penaltyUsed > maxPenalty) return
            if (sb.length > maxLen) return
            if (bestPenaltyByYomi.size >= maxResults) return

            // ★ prefix検索: 途中でも leaf なら採用
            acceptIfLeaf(nodeIndex, penaltyUsed)

            // 入力を使い切ったら終了（prefixなのでここで止める）
            if (strIndex >= str.length) {
                terminalStates.add(
                    TypoSearchState(nodeIndex, penaltyUsed, sb.length, sb.toString())
                )
                return
            }
            if (sb.length >= maxLen) return

            val ch = str[strIndex]

            // ★ 候補をペナルティ昇順で展開（枝刈り）
            val candidates: List<TypoCandidate> = getTypoCandidates(ch)

            for (cand in candidates) {
                val nextPenalty = penaltyUsed + cand.penalty
                if (nextPenalty > maxPenalty) continue

                var childPos = firstChild(nodeIndex, succinctBitVector)
                while (childPos >= 0 && LBS[childPos]) {
                    val labelNodeId = succinctBitVector.rank1(childPos)
                    if (labelNodeId < labels.size && labels[labelNodeId] == cand.ch) {
                        sb.append(cand.ch)
                        dfs(strIndex + 1, childPos, nextPenalty)
                        sb.setLength(sb.length - 1)
                        break
                    }
                    childPos++
                }
            }
        }

        // ルート開始
        dfs(strIndex = startIndex, nodeIndex = 0, penaltyUsed = 0)

        // 出力整形: penalty昇順 → 長さ降順（好み）→ 文字列昇順
        val results = bestPenaltyByYomi.entries
            .sortedWith(
                compareBy<Map.Entry<String, Int>> { it.value }
                    .thenByDescending { it.key.length }
                    .thenBy { it.key }
            )
            .map { TypoCorrectionResult(it.key, it.value) }
        return TypoSearchProgress(results, terminalStates, bestPenaltyByYomi)
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
                }
            }
        }
        val results = newResults.entries
            .sortedWith(
                compareBy<Map.Entry<String, Int>> { it.value }
                    .thenByDescending { it.key.length }
                    .thenBy { it.key }
            )
            .map { TypoCorrectionResult(it.key, it.value) }
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


}
