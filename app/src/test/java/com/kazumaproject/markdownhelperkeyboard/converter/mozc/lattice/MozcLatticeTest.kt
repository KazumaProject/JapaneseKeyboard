package com.kazumaproject.markdownhelperkeyboard.converter.mozc.lattice

import com.kazumaproject.markdownhelperkeyboard.converter.mozc.model.MozcNode
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.model.MozcNodeType
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MozcLatticeTest {

    @Test
    fun setKeyAddsBosAndEos() {
        val lattice = MozcLattice()

        lattice.setKey("きょう")

        assertTrue(lattice.endNodes(0).any { it.nodeType == MozcNodeType.BOS_NODE })
        assertTrue(lattice.beginNodes("きょう".length).any { it.nodeType == MozcNodeType.EOS_NODE })
    }

    @Test
    fun insertAddsNodeToBeginAndEndIndexes() {
        val lattice = MozcLattice()
        lattice.setKey("あい")
        val node = MozcNode(
            rid = 1,
            lid = 1,
            beginPos = 0,
            endPos = 1,
            wcost = 10,
            key = "あ",
            value = "A",
        )

        lattice.insert(node)

        assertSame(node, lattice.beginNodes(0).last())
        assertSame(node, lattice.endNodes(1).last())
    }
}
