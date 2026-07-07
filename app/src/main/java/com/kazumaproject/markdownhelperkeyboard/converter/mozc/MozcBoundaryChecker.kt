package com.kazumaproject.markdownhelperkeyboard.converter.mozc

import com.kazumaproject.graph.MozcNodeType
import com.kazumaproject.graph.Node

class MozcBoundaryChecker(
    private val segmenter: MozcSegmenter,
    private val mode: MozcBoundaryMode,
) {
    fun check(
        left: Node,
        right: Node,
        isEdge: Boolean,
    ): MozcBoundaryCheckResult {
        if (left.mozcNodeType == MozcNodeType.CON ||
            right.mozcNodeType == MozcNodeType.CON
        ) {
            return MozcBoundaryCheckResult.VALID
        }
        if (isBetweenAlphabetKeys(left, right)) {
            return MozcBoundaryCheckResult.INVALID
        }
        return when (mode) {
            MozcBoundaryMode.STRICT -> checkStrict(left, right, isEdge)
            MozcBoundaryMode.ONLY_MID -> checkOnlyMid(left, right, isEdge)
            MozcBoundaryMode.ONLY_EDGE -> checkOnlyEdge(left, right, isEdge)
        }
    }

    private fun checkOnlyMid(
        left: Node,
        right: Node,
        isEdge: Boolean,
    ): MozcBoundaryCheckResult {
        val isBoundary = left.mozcNodeType == MozcNodeType.HIS ||
            segmenter.isBoundary(left, right, isSingleSegment = false)
        if (!isEdge && isBoundary) {
            return MozcBoundaryCheckResult.INVALID
        }
        if (isEdge && !isBoundary) {
            return MozcBoundaryCheckResult.VALID_WEAK_CONNECTED
        }
        return MozcBoundaryCheckResult.VALID
    }

    private fun checkOnlyEdge(
        left: Node,
        right: Node,
        isEdge: Boolean,
    ): MozcBoundaryCheckResult {
        val isBoundary = left.mozcNodeType == MozcNodeType.HIS ||
            segmenter.isBoundary(left, right, isSingleSegment = true)
        return if (isEdge != isBoundary) {
            MozcBoundaryCheckResult.INVALID
        } else {
            MozcBoundaryCheckResult.VALID
        }
    }

    private fun checkStrict(
        left: Node,
        right: Node,
        isEdge: Boolean,
    ): MozcBoundaryCheckResult {
        val isBoundary = left.mozcNodeType == MozcNodeType.HIS ||
            segmenter.isBoundary(left, right, isSingleSegment = false)
        return if (isEdge != isBoundary) {
            MozcBoundaryCheckResult.INVALID
        } else {
            MozcBoundaryCheckResult.VALID
        }
    }

    private fun isBetweenAlphabetKeys(left: Node, right: Node): Boolean {
        val leftLast = left.yomiUsed.lastOrNull() ?: return false
        val rightFirst = right.yomiUsed.firstOrNull() ?: return false
        return leftLast.isAsciiAlphabet() && rightFirst.isAsciiAlphabet()
    }

    private fun Char.isAsciiAlphabet(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z'
}
