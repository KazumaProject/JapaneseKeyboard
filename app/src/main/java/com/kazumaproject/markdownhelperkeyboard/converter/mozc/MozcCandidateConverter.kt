package com.kazumaproject.markdownhelperkeyboard.converter.mozc

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

data class MozcConvertedCandidate(
    val candidate: Candidate,
    val path: MozcPath,
)

class MozcCandidateConverter {
    fun convert(paths: List<MozcPath>): List<MozcConvertedCandidate> =
        paths.mapNotNull { path ->
            if (path.nodes.isEmpty()) return@mapNotNull null
            val surface = path.nodes.joinToString("") { it.value }
            val yomi = path.nodes.joinToString("") { it.key }
            if (surface.isEmpty() || yomi.isEmpty()) return@mapNotNull null
            MozcConvertedCandidate(
                candidate = Candidate(
                    string = surface,
                    type = 1.toByte(),
                    length = yomi.length.toUByte(),
                    score = path.cost,
                    yomi = yomi,
                    leftId = path.nodes.first().leftId,
                    rightId = path.nodes.last().rightId,
                ),
                path = path,
            )
        }
}
