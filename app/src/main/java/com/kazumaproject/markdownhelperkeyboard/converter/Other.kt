package com.kazumaproject.markdownhelperkeyboard.converter

import com.kazumaproject.graph.Node
import com.kazumaproject.graph.MozcNodeType

object Other {
    val BOS = Node(
        l = 0,
        r = 0,
        score = 0,
        f = 0,
        g = 0,
        tango = "BOS",
        len = 0,
        yomiUsed = "BOS",
        sPos = 0,
        mozcNodeType = MozcNodeType.BOS,
    )
}
