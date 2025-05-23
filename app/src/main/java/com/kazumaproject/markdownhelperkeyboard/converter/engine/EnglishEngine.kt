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

    fun getCandidates(input: String, limit: Int): List<Candidate> {
        if (input.isEmpty()) return emptyList()

        // common constants
        val defaultType = 29.toByte()
        val inputLen = input.length.toUByte()
        val isFirstCapital = input[0].isUpperCase()
        val lowerInput = input.lowercase()
        val upperInput = input.uppercase()
        val capInput = input.replaceFirstChar { it.uppercaseChar() }

        // 1) predictive search
        val preds = englishLOUDS.predictiveSearch(lowerInput, englishSuccinctBitVectorLBS, limit)
        if (preds.isEmpty()) {
            // default fallback
            val out = ArrayList<Candidate>(3)
            if (isFirstCapital) {
                out += Candidate(capInput, defaultType, inputLen, 0)
                out += Candidate(input, defaultType, inputLen, 500)
                out += Candidate(upperInput, defaultType, inputLen, 2000)
            } else {
                out += Candidate(input, defaultType, inputLen, 0)
                out += Candidate(capInput, defaultType, inputLen, 500)
                out += Candidate(upperInput, defaultType, inputLen, 2000)
            }
            return out
        }

        // 2) build candidates for each prediction
        val out = ArrayList<Candidate>(preds.size)
        // cache these locally to avoid repeated property lookups
        val lbs = englishSuccinctBitVectorLBS
        val leaf = englishSuccinctBitVectorIsLeaf

        for (word in preds) {
            val idx = englishLOUDS.getNodeIndex(word, lbs)
            val base = englishLOUDS.getTermId(idx, leaf).toInt().takeIf { it >= 0 } ?: continue

            val wLen = word.length.toUByte()
            val wUp = word.uppercase()
            val wCap = word.replaceFirstChar { it.uppercaseChar() }
            val inputUp = input.uppercase()
            val inputWCap = input.replaceFirstChar { it.uppercaseChar() }

            if (isFirstCapital) {
                out += Candidate(wCap, defaultType, wLen, base - 1)
                out += Candidate(word, defaultType, wLen, base + 1500)
                out += Candidate(wUp, defaultType, wLen, base + 1000)
                out += Candidate(inputWCap, defaultType, wLen, base + 4000)
                out += Candidate(inputUp, defaultType, wLen, base + 5000)
                out += Candidate(input, defaultType, wLen, base + 5500)
            } else {
                out += Candidate(word, defaultType, wLen, base)
                out += Candidate(wCap, defaultType, wLen, base + 500)
                out += Candidate(wUp, defaultType, wLen, base + 2000)
                out += Candidate(inputWCap, defaultType, wLen, base + 5000)
                out += Candidate(inputUp, defaultType, wLen, base + 5500)
                out += Candidate(input, defaultType, wLen, base + 4000)
            }
        }

        // 3) sort once, in-place
        out.sortBy { it.score }
        return out
    }
}