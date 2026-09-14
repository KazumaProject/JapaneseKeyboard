package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.hiraToKata
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment

/** Reconstructs evidence from actual dictionary entries and validated numeric spans, without changing history. */
internal class NumberDictionaryResolver(
    private val yomi: LOUDSWithTermId,
    private val tango: LOUDS,
    private val tokens: TokenArray,
    private val yomiBits: SuccinctBitVector,
    private val leaves: SuccinctBitVector,
    private val tokenBits: SuccinctBitVector,
    private val tangoBits: SuccinctBitVector,
    private val additional: List<NumberDictionaryResolver> = emptyList(),
) {
    companion object {
        fun create(yomi: LOUDSWithTermId?, tango: LOUDS?, tokens: TokenArray?,
            yomiBits: SuccinctBitVector?, leaves: SuccinctBitVector?, tokenBits: SuccinctBitVector?,
            tangoBits: SuccinctBitVector?): NumberDictionaryResolver? {
            if (yomi == null || tango == null || tokens == null || yomiBits == null ||
                leaves == null || tokenBits == null || tangoBits == null) return null
            return NumberDictionaryResolver(yomi, tango, tokens, yomiBits, leaves, tokenBits, tangoBits)
        }
    }

    private fun entries(input: String, offset: Int): List<CandidateConversionSegment> =
        yomi.commonPrefixSearch(input.substring(offset), yomiBits).sortedByDescending { it.length }.flatMap { reading ->
            val term = yomi.getTermId(yomi.getNodeIndex(reading, yomiBits), leaves)
            tokens.getListDictionaryByYomiTermId(term, tokenBits).map { entry ->
                val surface = when (entry.nodeId) { -2 -> reading; -1 -> reading.hiraToKata()
                    else -> tango.getLetter(entry.nodeId, tangoBits) }
                CandidateConversionSegment(offset, offset + reading.length, surface, reading,
                    leftId = tokens.leftIds[entry.posTableIndex.toInt()],
                    startsWithParticle = tokens.leftIds[entry.posTableIndex.toInt()].toInt() in 268..433,
                    rightId = tokens.rightIds[entry.posTableIndex.toInt()])
            }
        }

    fun restore(candidate: Candidate): Candidate {
        val input = candidate.yomi ?: return candidate
        // Candidate.length is a UByte. Do not recursively reconstruct rows outside its range.
        if (input.length > UByte.MAX_VALUE.toInt()) return candidate
        if (candidate.commitText != candidate.string || candidate.conversionSegments.isNotEmpty()) return candidate
        // Numeric proofs need no dictionary lookup. Lexical numeric spellings do: graph
        // nodes must retain their dictionary provenance even when the raw pair is eligible.
        if (ValidatedNumber.parse(input) != null && NumberCandidatePolicy.eligible(input, candidate)) return candidate
        if (!NumberCandidatePolicy.needsDictionaryEvidence(input, candidate.string)) return candidate
        val output = candidate.string
        val entriesByOffset = mutableMapOf<Int, List<CandidateConversionSegment>>()
        fun entries(offset: Int): List<CandidateConversionSegment> = entriesByOffset.getOrPut(offset) {
            val numerical = (input.length downTo offset + 1).flatMap { end ->
                val reading = input.substring(offset, end)
                val proof = ValidatedNumber.parse(reading)
                proof?.basicForms?.map { surface -> CandidateConversionSegment(
                    offset, end, surface, reading, source = com.kazumaproject.graph.CandidateSource.UNKNOWN,
                ) }.orEmpty()
            }
            (entries(input, offset) + additional.flatMap { it.entries(input, offset) } + numerical).distinct()
        }
        val path = mutableListOf<CandidateConversionSegment>()
        // Corrupt/ambiguous old rows must not turn a keystroke into unbounded backtracking.
        // On exhaustion the original row still goes through ordinary eligibility; no rows
        // are deleted, and independently generated/dictionary candidates remain available.
        var remainingStates = 4096
        fun search(offset: Int, outOffset: Int): Candidate? {
            if (--remainingStates < 0) return null
            if (offset == input.length) {
                if (outOffset != output.length) return null
                val restored = candidate.copy(conversionSegments = path.toList())
                return restored.takeIf { NumberCandidatePolicy.eligible(input, it) }
            }
            for (entry in entries(offset)) {
                if (remainingStates < 0) return null
                if (entry.inputEnd <= offset || !output.startsWith(entry.output, outOffset)) continue
                path += entry
                val result = search(entry.inputEnd, outOffset + entry.output.length)
                path.removeAt(path.lastIndex)
                if (result != null) return result
            }
            return null
        }
        return search(0, 0) ?: candidate
    }
}
