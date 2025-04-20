package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.english.EnglishLOUDS

class EnglishEngine {
    private lateinit var englishLOUDS: EnglishLOUDS
    private lateinit var englishSuccinctBitVectorLBS: SuccinctBitVector
    private lateinit var englishSuccinctBitVectorIsLeaf: SuccinctBitVector
    fun buildEngine(
        englishLOUDS: EnglishLOUDS,
        englishSuccinctBitVectorLBS: SuccinctBitVector,
        englishSuccinctBitVectorIsLeaf: SuccinctBitVector
    ) {
        this.englishLOUDS = englishLOUDS
        this.englishSuccinctBitVectorLBS = englishSuccinctBitVectorLBS
        this.englishSuccinctBitVectorIsLeaf = englishSuccinctBitVectorIsLeaf
    }

    fun getCandidates(input: String): List<Candidate> {
        val predictSearch = englishLOUDS.predictiveSearch(input, englishSuccinctBitVectorLBS, 4)
        if (predictSearch.isEmpty()) {
            return listOf(
                Candidate(
                    string = input,
                    type = 29.toByte(),
                    length = input.length.toUByte(),
                    score = 0
                ),
                Candidate(
                    string = input.replaceFirstChar { it.uppercaseChar() },
                    type = 29.toByte(),
                    length = input.length.toUByte(),
                    score = 500
                ),
                Candidate(
                    string = input.uppercase(),
                    type = 29.toByte(),
                    length = input.length.toUByte(),
                    score = 2000
                )
            )
        } else {
            return predictSearch.flatMap { word ->
                val nodeIdx = englishLOUDS.getNodeIndex(word, englishSuccinctBitVectorLBS)
                val baseScore = englishLOUDS
                    .getTermId(
                        nodeIndex = nodeIdx,
                        succinctBitVector = englishSuccinctBitVectorIsLeaf
                    )
                    .toInt()

                if (baseScore < 0) {
                    emptyList()
                } else {
                    listOf(
                        Candidate(
                            string = word,
                            type = 29.toByte(),
                            length = word.length.toUByte(),
                            score = baseScore
                        ),
                        Candidate(
                            string = word.replaceFirstChar { it.uppercaseChar() },
                            type = 29.toByte(),
                            length = word.length.toUByte(),
                            score = baseScore + 500
                        ),
                        Candidate(
                            string = word.uppercase(),
                            type = 29.toByte(),
                            length = word.length.toUByte(),
                            score = baseScore + 2000
                        )
                    )
                }

            }.sortedBy { it.score }
        }
    }
}