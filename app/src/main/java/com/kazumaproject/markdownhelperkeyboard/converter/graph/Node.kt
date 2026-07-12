package com.kazumaproject.graph

enum class MozcNodeType {
    NOR,
    BOS,
    EOS,
    CON,
    HIS,
}

enum class CandidateSource {
    SYSTEM,
    UNKNOWN,
    USER_DICTIONARY,
    LEARNED_DICTIONARY,
}

object MozcNodeAttributes {
    const val NONE = 0
    const val STARTS_WITH_PARTICLE = 1
}

data class Node(
    val l: Short,
    val r: Short,
    var score: Int,
    var f: Int,
    var g: Int = 0,
    val tango: String,
    val len: Short,
    val yomiUsed: String,
    var sPos: Int,
    var prev: Node? = null,
    var next: Node? = null,
    var adjustedScore: Int = score,
    val mozcNodeType: MozcNodeType = MozcNodeType.NOR,
    val mozcAttributes: Int = MozcNodeAttributes.NONE,
    val candidateSource: CandidateSource = CandidateSource.SYSTEM,
) {
    override fun toString(): String {
        return this.tango
    }
}
