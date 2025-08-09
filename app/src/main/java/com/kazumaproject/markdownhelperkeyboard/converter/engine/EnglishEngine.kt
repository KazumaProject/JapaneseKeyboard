package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.english.louds.LOUDS
import com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id.LOUDSWithTermId
import com.kazumaproject.markdownhelperkeyboard.converter.english.tokenArray.TokenArray

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

    fun getCandidates(input: String): List<Candidate> {
        if (input.isEmpty()) return emptyList()

        // common constants
        val defaultType = 29.toByte()
        val lowerInput = input.lowercase()
        val limit = if (input.length <= 2) 8 else 16

        val predictiveSearchReading = readingLOUDS.predictiveSearch(
            prefix = lowerInput,
            succinctBitVector = succinctBitVectorLBSReading,
            limit = limit
        )

        val predictions = mutableListOf<Candidate>()
        predictions.add(
            Candidate(
                string = input.replaceFirstChar { it.uppercaseChar() },
                score = if (input.length <= 3) 9000 else if (input.length <= 4) 12000 else 57000,
                type = defaultType,
                length = input.length.toUByte()
            )
        )
        predictions.add(
            Candidate(
                string = input.uppercase(),
                score = if (input.length <= 3) 9001 else if (input.length <= 4) 22001 else 57001,
                type = defaultType,
                length = input.length.toUByte()
            )
        )
        if (predictiveSearchReading.isEmpty()) {
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
                    score = if (input.first().isUpperCase()) {
                        8500
                    } else {
                        10001
                    }
                ),

                Candidate(
                    string = input.uppercase(),
                    type = defaultType,
                    length = input.length.toUByte(),
                    score = 10002
                )
            )
        }
        for (readingStr in predictiveSearchReading) {
            val nodeIndex = readingLOUDS.getNodeIndex(
                readingStr,
                succinctBitVector = succinctBitVectorLBSReading
            )
            if (nodeIndex <= 0) continue

            val termId = readingLOUDS.getTermId(
                nodeIndex = nodeIndex,
                succinctBitVector = succinctBitVectorReadingIsLeaf
            )

            val listToken = tokenArray.getListDictionaryByYomiTermId(
                termId,
                succinctBitVectorTokenArray
            )

            // flatMap each token entry into three Candidate variants
            val variants = listToken.flatMap { entry ->
                // base string
                val base = when (entry.nodeId) {
                    -1 -> readingStr
                    else -> wordLOUDS.getLetter(
                        entry.nodeId,
                        succinctBitVector = succinctBitVectorLBSWord
                    )
                }

                // build the three variants
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
                        score = if (input.first()
                                .isUpperCase()
                        ) entry.wordCost.toInt() + base.length * LENGTH_MULTIPLY else entry.wordCost.toInt() + 500 + base.length * LENGTH_MULTIPLY
                    ),

                    Candidate(
                        string = base.uppercase(),
                        type = defaultType,
                        length = base.length.toUByte(),
                        score = if (input.first()
                                .isUpperCase()
                        ) entry.wordCost.toInt() + base.length * LENGTH_MULTIPLY else entry.wordCost.toInt() + 2000 + base.length * LENGTH_MULTIPLY
                    )
                )
            }

            predictions += variants
        }

        return predictions.sortedBy { it.score }
    }

}
