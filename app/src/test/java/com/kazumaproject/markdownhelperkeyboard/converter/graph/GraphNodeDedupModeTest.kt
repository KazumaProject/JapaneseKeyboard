package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class GraphNodeDedupModeTest {

    @Test
    fun existingModeReplacesSameTangoLeftRightWithLowerCost() {
        val graph = mutableMapOf<Int, MutableList<Node>>()
        val builder = GraphBuilder()

        invokeAddOrUpdate(builder, graph, node(score = 1000), GraphNodeDedupMode.EXISTING_BY_TANGO_L_R)
        invokeAddOrUpdate(builder, graph, node(score = 500), GraphNodeDedupMode.EXISTING_BY_TANGO_L_R)
        invokeAddOrUpdate(builder, graph, node(score = 700), GraphNodeDedupMode.EXISTING_BY_TANGO_L_R)

        assertEquals(1, graph.getValue(1).size)
        assertEquals(500, graph.getValue(1).single().score)
    }

    @Test
    fun mozcModeKeepsSameTangoLeftRightNodes() {
        val graph = mutableMapOf<Int, MutableList<Node>>()
        val builder = GraphBuilder()

        invokeAddOrUpdate(builder, graph, node(score = 1000), GraphNodeDedupMode.MOZC_KEEP_ALL)
        invokeAddOrUpdate(builder, graph, node(score = 500), GraphNodeDedupMode.MOZC_KEEP_ALL)

        assertEquals(2, graph.getValue(1).size)
        assertEquals(listOf(1000, 500), graph.getValue(1).map { it.score })
    }

    @Test
    fun equalClassifiedTokensMergeLabelsInBothModesButDifferentCostsDoNot() {
        GraphNodeDedupMode.entries.forEach { mode ->
            val graph = mutableMapOf<Int, MutableList<Node>>()
            val builder = GraphBuilder()
            invokeAddOrUpdate(builder, graph, node(1000).copy(dictionaryCategories = setOf(DictionaryCategory.CLASSIFIED_PERSON)), mode)
            invokeAddOrUpdate(builder, graph, node(1000).copy(dictionaryCategories = setOf(DictionaryCategory.CLASSIFIED_PLACE)), mode)
            assertEquals(setOf(DictionaryCategory.CLASSIFIED_PERSON, DictionaryCategory.CLASSIFIED_PLACE), graph.getValue(1).single().dictionaryCategories)
            invokeAddOrUpdate(builder, graph, node(500).copy(dictionaryCategories = setOf(DictionaryCategory.CLASSIFIED_CHARACTER)), mode)
            if (mode == GraphNodeDedupMode.EXISTING_BY_TANGO_L_R) {
                assertEquals(setOf(DictionaryCategory.CLASSIFIED_CHARACTER), graph.getValue(1).single().dictionaryCategories)
            } else {
                assertEquals(2, graph.getValue(1).size)
            }
        }
    }

    private fun invokeAddOrUpdate(
        builder: GraphBuilder,
        graph: MutableMap<Int, MutableList<Node>>,
        node: Node,
        mode: GraphNodeDedupMode,
    ) {
        val method = GraphBuilder::class.java.getDeclaredMethod(
            "addOrUpdateNode",
            MutableMap::class.java,
            Int::class.javaPrimitiveType,
            Node::class.java,
            GraphNodeDedupMode::class.java,
            MutableList::class.java,
            String::class.java,
            String::class.java,
        )
        method.isAccessible = true
        method.invoke(builder, graph, 1, node, mode, null, "よみ", "TEST")
    }

    private fun node(score: Int): Node =
        Node(
            l = 1.toShort(),
            r = 2.toShort(),
            score = score,
            f = score,
            tango = "語",
            len = 1.toShort(),
            yomiUsed = "よ",
            sPos = 0,
        )
}
