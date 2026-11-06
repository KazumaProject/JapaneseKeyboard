package com.kazumaproject.markdownhelperkeyboard.converter

import com.kazumaproject.graph.CandidateSource
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcSegmenter
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.LatticeEdgeCost
import java.io.File
import kotlin.math.roundToInt

internal class ExperimentalLatticeCosts(
    private val words: Map<String, Int>,
    private val pairs: Map<String, Map<String, Int>>,
    private val positions: Map<Int, Int>,
) {
    fun forInput(
        wordScale: Float,
        includeUnigrams: Boolean = true,
        boundaryCosts: IntArray? = null,
        segmenter: MozcSegmenter? = null,
    ): LatticeEdgeCost =
        LatticeEdgeCost { previous, current ->
            if (current.len <= 0 || current.candidateSource != CandidateSource.SYSTEM) {
                0
            } else {
                val pos = (previous.r.toInt() and 0xffff) shl 16 or (current.l.toInt() and 0xffff)
                val cost = (if (includeUnigrams) words[current.tango] ?: 0 else 0) +
                    (pairs[previous.tango]?.get(current.tango) ?: 0) + (positions[pos] ?: 0)
                val boundary = if (boundaryCosts != null &&
                    segmenter?.isBoundary(previous.r.toInt(), current.l.toInt()) == true
                ) boundaryCosts.getOrElse(current.sPos) { 0 } else 0
                (wordScale * cost).roundToInt() + boundary
            }
        }

    companion object {
        fun load(file: File): ExperimentalLatticeCosts {
            val words = HashMap<String, Int>()
            val pairs = HashMap<String, MutableMap<String, Int>>()
            val positions = HashMap<Int, Int>()
            file.useLines { lines ->
                for (line in lines) {
                    val columns = line.split('\t')
                    val cost = columns.last().toInt()
                    require(cost in -2000..2000)
                    when (columns.first()) {
                        "word" -> {
                            require(columns.size == 3)
                            words[columns[1]] = cost
                        }
                        "pair" -> {
                            require(columns.size == 4)
                            pairs.getOrPut(columns[1], ::HashMap)[columns[2]] = cost
                        }
                        "pos" -> {
                            require(columns.size == 4)
                            val right = columns[1].toInt()
                            val left = columns[2].toInt()
                            require(right in 0..0xffff && left in 0..0xffff)
                            positions[(right shl 16) or left] = cost
                        }
                        else -> error("Unknown lattice cost feature.")
                    }
                }
            }
            return ExperimentalLatticeCosts(words, pairs, positions)
        }
    }
}
