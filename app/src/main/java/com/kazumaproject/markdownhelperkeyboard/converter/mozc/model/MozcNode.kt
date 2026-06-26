package com.kazumaproject.markdownhelperkeyboard.converter.mozc.model

data class MozcNode(
    var prev: MozcNode? = null,
    var next: MozcNode? = null,
    var rid: Short,
    var lid: Short,
    var beginPos: Int,
    var endPos: Int,
    var wcost: Int,
    var cost: Int = Int.MAX_VALUE,
    var nodeType: MozcNodeType = MozcNodeType.NOR_NODE,
    var attributes: Int = 0,
    var key: String,
    var value: String,
)

enum class MozcNodeType {
    NOR_NODE,
    BOS_NODE,
    EOS_NODE,
    CON_NODE,
    HIS_NODE,
}
