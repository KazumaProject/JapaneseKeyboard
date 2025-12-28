package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.english.louds.LOUDS
import com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id.LOUDSWithTermId
import com.kazumaproject.markdownhelperkeyboard.converter.english.tokenArray.TokenArray
import timber.log.Timber

class EnglishEngine {
    private lateinit var readingLOUDS: LOUDSWithTermId
    private lateinit var wordLOUDS: LOUDS
    private lateinit var tokenArray: TokenArray
    private lateinit var succinctBitVectorLBSReading: SuccinctBitVector
    private lateinit var succinctBitVectorReadingIsLeaf: SuccinctBitVector
    private lateinit var succinctBitVectorTokenArray: SuccinctBitVector
    private lateinit var succinctBitVectorLBSWord: SuccinctBitVector

    companion object {
        const val LENGTH_MULTIPLY = 2000
    }

    fun buildEngine(
        englishReadingLOUDS: LOUDSWithTermId,
        englishWordLOUDS: LOUDS,
        englishTokenArray: TokenArray,
        englishSuccinctBitVectorLBSReading: SuccinctBitVector,
        englishSuccinctBitVectorLBSWord: SuccinctBitVector,
        englishSuccinctBitVectorReadingIsLeaf: SuccinctBitVector,
        englishSuccinctBitVectorTokenArray: SuccinctBitVector,
    ) {
        this.readingLOUDS = englishReadingLOUDS
        this.wordLOUDS = englishWordLOUDS
        this.tokenArray = englishTokenArray
        this.succinctBitVectorLBSReading = englishSuccinctBitVectorLBSReading
        this.succinctBitVectorLBSWord = englishSuccinctBitVectorLBSWord
        this.succinctBitVectorReadingIsLeaf = englishSuccinctBitVectorReadingIsLeaf
        this.succinctBitVectorTokenArray = englishSuccinctBitVectorTokenArray
    }

    fun getCandidates(input: String, enableTypoCorrection: Boolean = true): List<Candidate> {
        if (input.isEmpty()) return emptyList()

        val defaultType = 29.toByte()
        val lowerInput = input.lowercase()
        val limit = if (input.length <= 2) 6 else 12

        val predictiveSearchReading = readingLOUDS.predictiveSearch(
            prefix = lowerInput,
            succinctBitVector = succinctBitVectorLBSReading,
            limit = limit
        )

        // ★ typo はフラグが true のときだけ
        val typoCorrection = if (enableTypoCorrection) {
            readingLOUDS.commonPrefixSearchWithOmission(
                str = lowerInput,
                succinctBitVector = succinctBitVectorLBSReading
            ).filter { it.yomi.length == lowerInput.length }
        } else {
            emptyList()
        }

        Timber.d(
            "getCandidates English: [$input] predictive=$predictiveSearchReading typoEnabled=$enableTypoCorrection typo=[${typoCorrection.map { it.yomi }}]"
        )

        val predictions = mutableListOf<Candidate>()

        // input 自体の3種は常に入れる
        predictions += Candidate(
            string = input,
            score = 500,
            type = defaultType,
            length = input.length.toUByte()
        )
        predictions += Candidate(
            string = input.replaceFirstChar { it.uppercaseChar() },
            score = if (input.length <= 3) 9000 else if (input.length <= 4) 12000 else 57000,
            type = defaultType,
            length = input.length.toUByte()
        )
        predictions += Candidate(
            string = input.uppercase(),
            score = if (input.length <= 3) 9001 else if (input.length <= 4) 22001 else 57001,
            type = defaultType,
            length = input.length.toUByte()
        )

        // ★ fallback は predictive も (typo有効時のtypoも) 空のときだけ
        if (predictiveSearchReading.isEmpty() && typoCorrection.isEmpty()) {
            return listOf(
                Candidate(
                    string = input,
                    type = defaultType,
                    length = input.length.toUByte(),
                    score = 10000
                ),
                Candidate(
                    string = input.replaceFirstChar { it.uppercaseChar() },
                    type = defaultType,
                    length = input.length.toUByte(),
                    score = if (input.first().isUpperCase()) 8500 else 10001
                ),
                Candidate(
                    string = input.uppercase(),
                    type = defaultType,
                    length = input.length.toUByte(),
                    score = 10002
                )
            ).sortedBy { it.score }
        }
        // ============
        // 1) predictive（通常）
        // ============
        for (readingStr in predictiveSearchReading) {
            val nodeIndex = readingLOUDS.getNodeIndex(
                readingStr,
                succinctBitVector = succinctBitVectorLBSReading
            )
            if (nodeIndex <= 0) continue

            val termId = readingLOUDS.getTermId(
                nodeIndex,
                succinctBitVector = succinctBitVectorReadingIsLeaf
            )
            if (termId < 0) continue

            val listToken =
                tokenArray.getListDictionaryByYomiTermId(termId, succinctBitVectorTokenArray)

            val variants = listToken.flatMap { entry ->
                val base = when (entry.nodeId) {
                    -1 -> readingStr
                    else -> wordLOUDS.getLetter(
                        entry.nodeId,
                        succinctBitVector = succinctBitVectorLBSWord
                    )
                }

                listOf(
                    Candidate(
                        string = base,
                        type = defaultType,
                        length = base.length.toUByte(),
                        score = entry.wordCost.toInt()
                    ),
                    Candidate(
                        string = base.replaceFirstChar { it.uppercaseChar() },
                        type = defaultType,
                        length = base.length.toUByte(),
                        score = if (input.first().isUpperCase())
                            (entry.wordCost.toInt() + base.length * LENGTH_MULTIPLY - 8000).coerceAtLeast(
                                0
                            )
                        else
                            entry.wordCost.toInt() + 500 + base.length * LENGTH_MULTIPLY
                    ),
                    Candidate(
                        string = base.uppercase(),
                        type = defaultType,
                        length = base.length.toUByte(),
                        score = if (input.first().isUpperCase())
                            entry.wordCost.toInt() + base.length * LENGTH_MULTIPLY
                        else
                            entry.wordCost.toInt() + 2000 + base.length * LENGTH_MULTIPLY
                    )
                )
            }

            predictions += variants
        }

        // ============
        // 2) typo（補正） - enableTypoCorrection のときだけ
        // ============
        if (enableTypoCorrection && typoCorrection.isNotEmpty()) {
            val typoType = 34.toByte()
            val predictiveSet = predictiveSearchReading.toHashSet()

            val maxEdits = maxEditsByLength(lowerInput.length)

            for (typo in typoCorrection) {
                val readingStr = typo.yomi
                if (!predictiveSet.add(readingStr)) continue

                val nodeIndex = readingLOUDS.getNodeIndex(
                    readingStr,
                    succinctBitVector = succinctBitVectorLBSReading
                )
                if (nodeIndex <= 0) continue

                val termId = readingLOUDS.getTermId(
                    nodeIndex,
                    succinctBitVector = succinctBitVectorReadingIsLeaf
                )
                if (termId < 0) continue

                val listToken =
                    tokenArray.getListDictionaryByYomiTermId(termId, succinctBitVectorTokenArray)

                val penalty = if (typo.omissionOccurred) 9000 else 8000

                val variants = listToken.flatMap { entry ->
                    val base = when (entry.nodeId) {
                        -1 -> readingStr
                        else -> wordLOUDS.getLetter(
                            entry.nodeId,
                            succinctBitVector = succinctBitVectorLBSWord
                        )
                    }

                    // ★ここで「input と似ているものだけ」に絞る（typo由来のみ）
                    val baseLower = base.lowercase()
                    if (!withinEditDistance(baseLower, lowerInput, maxEdits)) {
                        return@flatMap emptyList<Candidate>()
                    }

                    listOf(
                        Candidate(
                            base,
                            typoType,
                            base.length.toUByte(),
                            entry.wordCost.toInt() + penalty
                        ),
                        Candidate(
                            base.replaceFirstChar { it.uppercaseChar() },
                            typoType,
                            base.length.toUByte(),
                            entry.wordCost.toInt() + 500 + base.length * LENGTH_MULTIPLY + penalty
                        ),
                        Candidate(
                            base.uppercase(),
                            typoType,
                            base.length.toUByte(),
                            entry.wordCost.toInt() + 2000 + base.length * LENGTH_MULTIPLY + penalty
                        )
                    )
                }

                predictions += variants
            }
        }

        // 同一文字列は最小スコアのみ残す
        val deduped = predictions
            .groupBy { it.string }
            .map { (_, list) -> list.minBy { it.score } }

        return deduped.sortedBy { it.score }
    }

    private fun withinEditDistance(a: String, b: String, maxEdits: Int): Boolean {
        val la = a.length
        val lb = b.length
        if (kotlin.math.abs(la - lb) > maxEdits) return false
        if (maxEdits == 0) return a == b
        if (a == b) return true

        // DP 1行で、maxEdits 超えたら早期終了
        var prev = IntArray(lb + 1) { it }
        var curr = IntArray(lb + 1)

        for (i in 1..la) {
            curr[0] = i
            var rowMin = curr[0]
            val ca = a[i - 1]
            for (j in 1..lb) {
                val cost = if (ca == b[j - 1]) 0 else 1
                val v = minOf(
                    prev[j] + 1,        // delete
                    curr[j - 1] + 1,    // insert
                    prev[j - 1] + cost  // replace
                )
                curr[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > maxEdits) return false
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[lb] <= maxEdits
    }

    private fun maxEditsByLength(len: Int): Int = when {
        len <= 3 -> 1
        len <= 6 -> 1
        len <= 10 -> 2
        else -> 3
    }

}
