package com.kazumaproject.markdownhelperkeyboard.converter.mozc

class MozcBoundaryDetector(
    private val segmenter: MozcSegmenter,
) {
    fun isBoundary(leftNode: MozcNode, rightNode: MozcNode): Boolean {
        return segmenter.isBoundary(leftNode, rightNode)
    }
}
