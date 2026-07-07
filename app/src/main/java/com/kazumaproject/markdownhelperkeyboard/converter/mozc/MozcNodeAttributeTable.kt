package com.kazumaproject.markdownhelperkeyboard.converter.mozc

import com.kazumaproject.graph.MozcNodeAttributes

class MozcNodeAttributeTable(
    private val attributesByLid: IntArray,
) {
    fun attributesFor(lid: Int): Int {
        if (lid !in attributesByLid.indices) {
            return MozcNodeAttributes.NONE
        }
        return attributesByLid[lid]
    }
}
